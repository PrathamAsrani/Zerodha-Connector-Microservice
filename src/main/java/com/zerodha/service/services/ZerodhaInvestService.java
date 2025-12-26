package com.zerodha.service.services;

import com.zerodha.service.dtos.historical_data.CandleData;
import com.zerodha.service.dtos.historical_data.HistoricalDataRequestDto;
import com.zerodha.service.dtos.historical_data.HistoricalDataResponseDto;
import com.zerodha.service.dtos.historical_data.InstrumentInfoDto;
import com.zerodha.service.dtos.order.OrderRequestDto;
import com.zerodha.service.dtos.order.OrderResponseDto;
import com.zerodha.service.enums.ExchangeEnum;
import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Instrument;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import com.zerodha.service.enums.OrderEnum;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.zerodha.service.constants.MarketConstants.IS_MARKET_OPEN;

@RequiredArgsConstructor
@Service
public class ZerodhaInvestService {
    private final KiteUtility kiteUtility;
    private OrderService orderService;
    private final ZerodhaAuthService authService;
    private Logger logger= LoggerFactory.getLogger(ZerodhaInvestService.class);

    // Cache for instruments to avoid repeated API calls
    private List<Instrument> instrumentsCache;
    private LocalDateTime instrumentsCacheTime;
    private static final long CACHE_VALIDITY_HOURS = 24;

    public ResponseEntity<OrderResponseDto> placeOrder(OrderRequestDto orderRequestDto){
        logger.info("Placing order.");
        if(!IS_MARKET_OPEN()){
            logger.error("Market is closed.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new OrderResponseDto("Market is closed"));
        }
        try{
            KiteConnect kiteSdk = kiteUtility.getKiteSdk();
            logger.info("KiteSdk got");

            OrderParams orderParams = getOrderParams(orderRequestDto); 
            logger.info("Params set, Creating order.");
            Order order = kiteSdk.placeOrder(orderParams , orderRequestDto.variety.getCode());
            logger.info("Order Placed!");
            logger.info("order: " + order.toString());

            orderRequestDto.price = Double.parseDouble(order.averagePrice);
            boolean result = orderService.saveOrder(orderRequestDto , order.orderId);

            if(result)
                logger.info("Order saved in DB");
            else
                logger.info("Failed to save in DB");

            return ResponseEntity.ok(new OrderResponseDto(order.orderId));
        }
       catch (IOException ex) {
           logger.error("IOException caught", ex);
           return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                   .body(null);
        }
        catch (KiteException ex) {
            logger.error("KiteException caught", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);
        }
        catch (Exception ex){
            logger.error("Exception caught", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);
        }
    }

    // helpers
    private OrderParams getOrderParams(OrderRequestDto orderRequestDto){
        OrderParams orderParams = new OrderParams();
        logger.info("Setting params");
        if(orderRequestDto.orderType == null || orderRequestDto.orderType != OrderEnum.MARKET) {
            orderParams.price = orderRequestDto.price;
            orderParams.triggerPrice = orderRequestDto.triggerPrice;
        }
        orderParams.quantity = orderRequestDto.quantity;
        orderParams.orderType = orderRequestDto.orderType.getCode();
        orderParams.tradingsymbol = orderRequestDto.tradingsymbol;
        orderParams.product =  orderRequestDto.product.getCode();
        orderParams.exchange = orderRequestDto.exchange.getCode();
        orderParams.transactionType = orderRequestDto.transactionType.getCode();
        orderParams.validity = orderRequestDto.validity.getCode();
        orderParams.tag = orderRequestDto.tag;
        orderParams.disclosedQuantity=orderRequestDto.disclosedQuantity;
        orderParams.squareoff=orderRequestDto.squareoffValue;
        orderParams.stoploss=orderRequestDto.stoplossValue;
        orderParams.trailingStoploss=orderRequestDto.trailingStoploss;
        logger.info(
        "FINAL ORDER => exchange={}, symbol={}, txn={}, qty={}, type={}, product={}, price={}, trigger={}",
            orderParams.exchange,
            orderParams.tradingsymbol,
            orderParams.transactionType,
            orderParams.quantity,
            orderParams.orderType,
            orderParams.product,
            orderParams.price,
            orderParams.triggerPrice
        );
        return orderParams;
    }

    public HistoricalDataResponseDto getHistoricalData(HistoricalDataRequestDto request) {
        logger.info("Fetching historical data for symbol: {}, exchange: {}, from: {}, to: {}",
                request.getTradingSymbol(), request.getExchange(), request.getFromDate(), request.getToDate());

        try {
            // Get authenticated KiteConnect instance
            KiteConnect kiteSdk = kiteUtility.getKiteSdk();

            // Get instrument token if not provided
            String instrumentToken = request.getInstrumentToken();
            if (instrumentToken == null || instrumentToken.isEmpty()) {
                logger.info("Instrument token not provided. Searching for symbol: {}", request.getTradingSymbol());
                InstrumentInfoDto instrumentInfo = findInstrument(request.getTradingSymbol(), request.getExchange());

                if (instrumentInfo == null) {
                    logger.error("Instrument not found for symbol: {}, exchange: {}",
                            request.getTradingSymbol(), request.getExchange());
                    return HistoricalDataResponseDto.builder()
                            .success(false)
                            .message("Instrument not found for symbol: " + request.getTradingSymbol())
                            .build();
                }

                instrumentToken = instrumentInfo.getInstrumentToken().toString();
                logger.info("Found instrument token: {} for symbol: {}", instrumentToken, request.getTradingSymbol());
            }

            // Set default interval if not provided
            String interval = request.getInterval() != null ? request.getInterval() : "day";

            // Set continuous to true by default for adjusted data
            boolean continuous = request.getContinuous() != null ? request.getContinuous() : true;

            // Convert dates to required format
            Date fromDate = Date.from(request.getFromDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date toDate = Date.from(request.getToDate().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());

            logger.info("Fetching historical data from Zerodha API. Token: {}, Interval: {}, Continuous: {}",
                    instrumentToken, interval, continuous);

            // Fetch historical data from Zerodha
            HistoricalData historicalData = kiteSdk.getHistoricalData(
                    fromDate,
                    toDate,
                    instrumentToken,
                    interval,
                    continuous,
                    request.getOi() != null ? request.getOi() : false
            );

            // Convert to response DTO
            List<CandleData> candles = convertToCandles(historicalData);

            logger.info("Successfully fetched {} candles for symbol: {}", candles.size(), request.getTradingSymbol());

            return HistoricalDataResponseDto.builder()
                    .instrumentToken(instrumentToken)
                    .tradingSymbol(request.getTradingSymbol())
                    .exchange(request.getExchange())
                    .fromDate(request.getFromDate())
                    .toDate(request.getToDate())
                    .interval(interval)
                    .candles(candles)
                    .totalRecords(candles.size())
                    .success(true)
                    .message("Historical data fetched successfully")
                    .build();

        } catch (KiteException e) {
            logger.error("KiteException while fetching historical data: {}", e.getMessage(), e);
            return HistoricalDataResponseDto.builder()
                    .success(false)
                    .message("Zerodha API error: " + e.getMessage())
                    .build();
        } catch (IOException e) {
            logger.error("IOException while fetching historical data: {}", e.getMessage(), e);
            return HistoricalDataResponseDto.builder()
                    .success(false)
                    .message("Network error: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            logger.error("Unexpected error while fetching historical data: {}", e.getMessage(), e);
            return HistoricalDataResponseDto.builder()
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .build();
        }
    }

    public InstrumentInfoDto findInstrument(String tradingSymbol, ExchangeEnum exchange) {
        logger.info("Searching for instrument: {}, exchange: {}", tradingSymbol, exchange);

        try {
            List<Instrument> instruments = getInstruments();

            // Search for exact match first
            Optional<Instrument> instrumentOpt = instruments.stream()
                    .filter(i -> i.tradingsymbol.equalsIgnoreCase(tradingSymbol))
                    .filter(i -> exchange == null || i.exchange.equalsIgnoreCase(String.valueOf(exchange)))
                    .filter(i -> "EQ".equals(i.segment) || "NSE".equals(i.exchange) || "BSE".equals(i.exchange))
                    .findFirst();

            if (instrumentOpt.isPresent()) {
                Instrument instrument = instrumentOpt.get();
                logger.info("Found instrument: {} (Token: {})", instrument.tradingsymbol, instrument.instrument_token);

                return InstrumentInfoDto.builder()
                        .instrumentToken(instrument.instrument_token)
                        .exchangeToken(instrument.exchange_token)
                        .tradingSymbol(instrument.tradingsymbol)
                        .name(instrument.name)
                        .lastPrice(instrument.last_price)
                        .tickSize(instrument.tick_size)
                        .lotSize(instrument.lot_size)
                        .instrumentType(instrument.instrument_type)
                        .segment(instrument.segment)
                        .exchange(instrument.exchange)
                        .build();
            }

            logger.warn("Instrument not found: {}, exchange: {}", tradingSymbol, exchange);
            return null;

        } catch (Exception e) {
            logger.error("Error searching for instrument: {}", e.getMessage(), e);
            return null;
        } catch (KiteException e) {
            throw new RuntimeException(e);
        }
    }

    public List<InstrumentInfoDto> searchInstruments(String tradingSymbol, String exchange) {
        logger.info("Searching instruments matching: {}, exchange: {}", tradingSymbol, exchange);

        try {
            List<Instrument> instruments = getInstruments();

            return instruments.stream()
                    .filter(i -> i.tradingsymbol.toLowerCase().contains(tradingSymbol.toLowerCase()))
                    .filter(i -> exchange == null || i.exchange.equalsIgnoreCase(exchange))
                    .map(instrument -> InstrumentInfoDto.builder()
                            .instrumentToken(instrument.instrument_token)
                            .exchangeToken(instrument.exchange_token)
                            .tradingSymbol(instrument.tradingsymbol)
                            .name(instrument.name)
                            .lastPrice(instrument.last_price)
                            .expiry(instrument.expiry != null ?
                                    instrument.expiry.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null)
                            .strike(Double.valueOf(instrument.strike))
                            .tickSize(instrument.tick_size)
                            .lotSize(instrument.lot_size)
                            .instrumentType(instrument.instrument_type)
                            .segment(instrument.segment)
                            .exchange(instrument.exchange)
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error searching instruments: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (KiteException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Instrument> getInstruments() throws KiteException, IOException {
        // Check cache validity
        if (instrumentsCache != null && instrumentsCacheTime != null) {
            long hoursSinceCache = java.time.Duration.between(instrumentsCacheTime, LocalDateTime.now()).toHours();
            if (hoursSinceCache < CACHE_VALIDITY_HOURS) {
                logger.debug("Using cached instruments. Cache age: {} hours", hoursSinceCache);
                return instrumentsCache;
            }
        }

        logger.info("Fetching instruments from Zerodha API (cache expired or not available)");
        KiteConnect kiteSdk = kiteUtility.getKiteSdk();

        instrumentsCache = kiteSdk.getInstruments();
        instrumentsCacheTime = LocalDateTime.now();

        logger.info("Cached {} instruments", instrumentsCache.size());
        return instrumentsCache;
    }

    public void clearInstrumentsCache() {
        logger.info("Clearing instruments cache");
        instrumentsCache = null;
        instrumentsCacheTime = null;
    }

    public HistoricalDataResponseDto getSwingTradingData(String tradingSymbol, ExchangeEnum exchange, int days) {
        logger.info("Fetching swing trading data for {} days for symbol: {}", days, tradingSymbol);

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(days);

        HistoricalDataRequestDto request = HistoricalDataRequestDto.builder()
                .tradingSymbol(tradingSymbol)
                .exchange(exchange != null ? exchange : ExchangeEnum.NSE)
                .fromDate(fromDate)
                .toDate(toDate)
                .interval("day")
                .continuous(true) // Adjusted for splits & bonuses
                .oi(false)
                .build();

        return getHistoricalData(request);
    }

    public CandleData getLatestCandle(String tradingSymbol, ExchangeEnum exchange) {
        logger.info("Fetching latest candle for symbol: {}", tradingSymbol);

        LocalDate today = LocalDate.now();
        HistoricalDataResponseDto response = getSwingTradingData(tradingSymbol, exchange, 1);

        if (response.getSuccess() && response.getCandles() != null && !response.getCandles().isEmpty()) {
            return response.getCandles().get(response.getCandles().size() - 1);
        }

        return null;
    }

    private List<CandleData> convertToCandles(HistoricalData historicalData) {
        if (historicalData == null || historicalData.dataArrayList == null) {
            return Collections.emptyList();
        }

        return historicalData.dataArrayList.stream()
                .map(candle -> CandleData.builder()
                        .timestamp(candle.timeStamp != null ? OffsetDateTime.parse(candle.timeStamp).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() : null)
                        .open(candle.open)
                        .high(candle.high)
                        .low(candle.low)
                        .close(candle.close)
                        .volume(candle.volume)
                        .openInterest(candle.oi)
                        .build())
                .collect(Collectors.toList());
    }
}
