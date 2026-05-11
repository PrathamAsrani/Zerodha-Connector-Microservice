package com.zerodha.service.controllers;

import com.zerodha.service.dtos.historical_data.CandleData;
import com.zerodha.service.dtos.historical_data.HistoricalDataRequestDto;
import com.zerodha.service.dtos.historical_data.HistoricalDataResponseDto;
import com.zerodha.service.dtos.historical_data.InstrumentInfoDto;
import com.zerodha.service.dtos.order.OrderRequestDto;
import com.zerodha.service.dtos.order.OrderResponseDto;
import com.zerodha.service.dtos.scraper.ScreenerLtpDto;
import com.zerodha.service.dtos.ticker.LtpResponseDto;
import com.zerodha.service.enums.ExchangeEnum;
import com.zerodha.service.services.OrderService;
import com.zerodha.service.services.ScreenerScraperService;
import com.zerodha.service.services.TickerService;
import com.zerodha.service.services.ZerodhaInvestService;
import org.jsoup.HttpStatusException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/invest")
@RequiredArgsConstructor
@Slf4j
public class Invest_Controller {

    private final ZerodhaInvestService zerodhaInvestService;
    private final OrderService orderService;
    private final TickerService tickerService;
    private final ScreenerScraperService screenerScraperService;

    // ==================== ORDER ENDPOINTS ====================

    /**
     * Place a new order
     * POST /invest/place-order
     */
    @PostMapping(
            value = "/place-order",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Async
    public CompletableFuture<ResponseEntity<OrderResponseDto>> placeOrder(@RequestBody OrderRequestDto orderRequestDto) {
        log.info("Place order request received for symbol: {}", orderRequestDto.tradingsymbol);
        return CompletableFuture.completedFuture(zerodhaInvestService.placeOrder(orderRequestDto));
    }

    /**
     * Get all orders
     * GET /invest/get-orders
     */
    @GetMapping("/get-orders")
    @Async
    public CompletableFuture<ResponseEntity<Object>> getOrders() {
        log.info("Get orders request received");
        return CompletableFuture.completedFuture(orderService.getOrders());
    }

    // ==================== PORTFOLIO ENDPOINTS ====================

    /**
     * Retrieve the list of long term equity holdings.
     * Mirrors Zerodha's GET /portfolio/holdings
     * GET /invest/holdings
     */
    @GetMapping("/holdings")
    @Async
    public CompletableFuture<ResponseEntity<Object>> getHoldings() {
        log.info("Get holdings request received");
        return CompletableFuture.completedFuture(zerodhaInvestService.getHoldings());
    }

    // ==================== LIVE PRICE (WEBSOCKET) ENDPOINTS ====================

    /**
     * Get the current live price (LTP) of a stock via Kite WebSocket streaming.
     * GET /invest/ltp?symbol=ITC&exchange=NSE&timeoutMs=5000
     */
    @GetMapping("/ltp")
    public ResponseEntity<Object> getLtp(
            @RequestParam("symbol") String tradingSymbol,
            @RequestParam(value = "exchange", defaultValue = "NSE") String exchange,
            @RequestParam(value = "timeoutMs", defaultValue = "5000") long timeoutMs) {

        log.info("LTP request: symbol={}, exchange={}", tradingSymbol, exchange);

        ExchangeEnum exchangeEnum;
        try {
            exchangeEnum = ExchangeEnum.valueOf(exchange.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid exchange: {}. Using NSE as default.", exchange);
            exchangeEnum = ExchangeEnum.NSE;
        }

        InstrumentInfoDto instrument = zerodhaInvestService.findInstrument(tradingSymbol, exchangeEnum);
        if (instrument == null || instrument.getInstrumentToken() == null) {
            log.error("Instrument not found: symbol={}, exchange={}", tradingSymbol, exchange);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Instrument not found: " + tradingSymbol));
        }

        try {
            Double ltp = tickerService.getLtp(instrument.getInstrumentToken(), timeoutMs);
            return ResponseEntity.ok(LtpResponseDto.builder()
                    .tradingSymbol(instrument.getTradingSymbol())
                    .exchange(instrument.getExchange())
                    .instrumentToken(instrument.getInstrumentToken())
                    .lastPrice(ltp)
                    .timestamp(Instant.now())
                    .build());
        } catch (TimeoutException ex) {
            log.warn("Timed out waiting for tick for symbol: {}", tradingSymbol);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(Map.of("message", "Timed out waiting for tick. Market may be closed or instrument inactive."));
        } catch (IllegalStateException ex) {
            log.error("Auth error: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to fetch LTP for {}: {}", tradingSymbol, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch LTP: " + ex.getMessage()));
        }
    }

    /**
     * Get the current price of a stock by scraping screener.in.
     * Useful as a fallback when Kite WebSocket data is unavailable (e.g. no live data subscription).
     * GET /invest/scrape-ltp?symbol=RELIANCE
     */
    @GetMapping("/scrape-ltp")
    public ResponseEntity<Object> scrapeLtp(@RequestParam("symbol") String symbol) {
        log.info("Scrape LTP request: symbol={}", symbol);
        try {
            ScreenerLtpDto dto = screenerScraperService.fetchLtp(symbol);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException ex) {
            log.warn("Bad symbol for scraper: {}", symbol);
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (HttpStatusException ex) {
            if (ex.getStatusCode() == 404) {
                log.warn("screener.in 404 for symbol: {}", symbol);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Symbol not found on screener.in: " + symbol));
            }
            log.error("screener.in returned {}: {}", ex.getStatusCode(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "screener.in error: " + ex.getStatusCode()));
        } catch (java.net.SocketTimeoutException ex) {
            log.warn("Timeout scraping screener.in for symbol: {}", symbol);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(Map.of("message", "Timed out fetching from screener.in"));
        } catch (Exception ex) {
            log.error("Failed to scrape LTP for {}: {}", symbol, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to scrape LTP: " + ex.getMessage()));
        }
    }

    // ==================== HISTORICAL DATA ENDPOINTS ====================

    /**
     * Get historical OHLCV data (adjusted for splits & bonuses)
     * GET /invest/historical?symbol=ITC&exchange=NSE&fromDate=2024-01-01&toDate=2024-12-26&interval=day
     *
     * @param tradingSymbol Trading symbol (e.g., "ITC", "RELIANCE")
     * @param exchange Exchange (NSE, BSE, NFO, etc.) - default: NSE
     * @param fromDate Start date (YYYY-MM-DD format)
     * @param toDate End date (YYYY-MM-DD format)
     * @param interval Candle interval (day, minute, 3minute, 5minute, 10minute, 15minute, 30minute, 60minute) - default: day
     * @param continuous Adjusted for splits & bonuses - default: true
     * @param instrumentToken Optional instrument token (if known)
     * @return Historical OHLCV data
     */
    @GetMapping("/historical")
    public ResponseEntity<HistoricalDataResponseDto> getHistoricalData(
            @RequestParam("symbol") String tradingSymbol,
            @RequestParam(value = "exchange", defaultValue = "NSE") String exchange,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "interval", defaultValue = "day") String interval,
            @RequestParam(value = "continuous", defaultValue = "true") Boolean continuous,
            @RequestParam(value = "instrumentToken", required = false) String instrumentToken) {

        log.info("Historical data request: symbol={}, exchange={}, from={}, to={}, interval={}",
                tradingSymbol, exchange, fromDate, toDate, interval);

        // Convert exchange string to enum
        ExchangeEnum exchangeEnum;
        try {
            exchangeEnum = ExchangeEnum.valueOf(exchange.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid exchange: {}. Using NSE as default.", exchange);
            exchangeEnum = ExchangeEnum.NSE;
        }

        HistoricalDataRequestDto request = HistoricalDataRequestDto.builder()
                .tradingSymbol(tradingSymbol)
                .exchange(exchangeEnum)
                .fromDate(fromDate)
                .toDate(toDate)
                .interval(interval)
                .continuous(continuous)
                .instrumentToken(instrumentToken)
                .build();

        HistoricalDataResponseDto response = zerodhaInvestService.getHistoricalData(request);

        if (response.getSuccess()) {
            log.info("Historical data fetched successfully. Total records: {}", response.getTotalRecords());
            return ResponseEntity.ok(response);
        } else {
            log.error("Failed to fetch historical data: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get swing trading data (default 90 days of daily candles)
     * GET /invest/swing-trading?symbol=ITC&exchange=NSE&days=90
     *
     * @param tradingSymbol Trading symbol
     * @param exchange Exchange - default: NSE
     * @param days Number of days - default: 90
     * @return Historical data for swing trading
     */
    @GetMapping("/swing-trading")
    public ResponseEntity<HistoricalDataResponseDto> getSwingTradingData(
            @RequestParam("symbol") String tradingSymbol,
            @RequestParam(value = "exchange", defaultValue = "NSE") String exchange,
            @RequestParam(value = "days", defaultValue = "90") int days) {

        log.info("Swing trading data request: symbol={}, exchange={}, days={}", tradingSymbol, exchange, days);

        // Convert exchange string to enum
        ExchangeEnum exchangeEnum;
        try {
            exchangeEnum = ExchangeEnum.valueOf(exchange.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid exchange: {}. Using NSE as default.", exchange);
            exchangeEnum = ExchangeEnum.NSE;
        }

        HistoricalDataResponseDto response = zerodhaInvestService.getSwingTradingData(tradingSymbol, exchangeEnum, days);

        if (response.getSuccess()) {
            log.info("Swing trading data fetched successfully. Total records: {}", response.getTotalRecords());
            return ResponseEntity.ok(response);
        } else {
            log.error("Failed to fetch swing trading data: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get latest candle (today's OHLCV data)
     * GET /invest/latest?symbol=ITC&exchange=NSE
     *
     * @param tradingSymbol Trading symbol
     * @param exchange Exchange - default: NSE
     * @return Latest candle data
     */
    @GetMapping("/latest")
    public ResponseEntity<CandleData> getLatestCandle(
            @RequestParam("symbol") String tradingSymbol,
            @RequestParam(value = "exchange", defaultValue = "NSE") String exchange) {

        log.info("Latest candle request: symbol={}, exchange={}", tradingSymbol, exchange);

        // Convert exchange string to enum
        ExchangeEnum exchangeEnum;
        try {
            exchangeEnum = ExchangeEnum.valueOf(exchange.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid exchange: {}. Using NSE as default.", exchange);
            exchangeEnum = ExchangeEnum.NSE;
        }

        CandleData candle = zerodhaInvestService.getLatestCandle(tradingSymbol, exchangeEnum);

        if (candle != null) {
            log.info("Latest candle fetched successfully for symbol: {}", tradingSymbol);
            return ResponseEntity.ok(candle);
        } else {
            log.error("Latest candle not found for symbol: {}, exchange: {}", tradingSymbol, exchange);
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== INSTRUMENT SEARCH ENDPOINTS ====================

    /**
     * Search for instruments by trading symbol
     * GET /invest/search?query=ITC&exchange=NSE
     *
     * @param query Search query (partial match supported)
     * @param exchange Optional exchange filter
     * @return List of matching instruments
     */
    @GetMapping("/search")
    public ResponseEntity<List<InstrumentInfoDto>> searchInstruments(
            @RequestParam("query") String query,
            @RequestParam(value = "exchange", required = false) String exchange) {

        log.info("Instrument search request: query={}, exchange={}", query, exchange);

        List<InstrumentInfoDto> instruments = zerodhaInvestService.searchInstruments(query, exchange);

        log.info("Found {} instruments matching query: {}", instruments.size(), query);
        return ResponseEntity.ok(instruments);
    }

    /**
     * Get exact instrument details
     * GET /invest/instrument?symbol=ITC&exchange=NSE
     *
     * @param tradingSymbol Exact trading symbol
     * @param exchange Exchange - default: NSE
     * @return Instrument details
     */
    @GetMapping("/instrument")
    public ResponseEntity<InstrumentInfoDto> getInstrument(
            @RequestParam("symbol") String tradingSymbol,
            @RequestParam(value = "exchange", defaultValue = "NSE") String exchange) {

        log.info("Instrument info request: symbol={}, exchange={}", tradingSymbol, exchange);

        // Convert exchange string to enum
        ExchangeEnum exchangeEnum;
        try {
            exchangeEnum = ExchangeEnum.valueOf(exchange.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid exchange: {}. Using NSE as default.", exchange);
            exchangeEnum = ExchangeEnum.NSE;
        }

        InstrumentInfoDto instrument = zerodhaInvestService.findInstrument(tradingSymbol, exchangeEnum);

        if (instrument != null) {
            log.info("Instrument found: {} (Token: {})", instrument.getTradingSymbol(), instrument.getInstrumentToken());
            return ResponseEntity.ok(instrument);
        } else {
            log.error("Instrument not found: symbol={}, exchange={}", tradingSymbol, exchange);
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== CACHE MANAGEMENT ENDPOINTS ====================

    /**
     * Clear instruments cache
     * POST /invest/cache/clear
     *
     * @return Success message
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearInstrumentsCache() {
        log.info("Cache clear request received");
        zerodhaInvestService.clearInstrumentsCache();
        log.info("Instruments cache cleared successfully");
        return ResponseEntity.ok("Instruments cache cleared successfully");
    }
}
