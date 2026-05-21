package com.zerodha.service.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.zerodha.service.dtos.historical_data.CandleData;
import com.zerodha.service.dtos.historical_data.HistoricalDataRequestDto;
import com.zerodha.service.dtos.historical_data.HistoricalDataResponseDto;
import com.zerodha.service.dtos.historical_data.InstrumentInfoDto;
import com.zerodha.service.dtos.order.OrderRequestDto;
import com.zerodha.service.dtos.order.OrderResponseDto;
import com.zerodha.service.dtos.portfolio.HoldingDto;
import com.zerodha.service.enums.ExchangeEnum;
import com.zerodha.service.model.HoldingEntity;
import com.zerodha.service.repository.HoldingRepo;
import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.PermissionException;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.TokenException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Holding;
import com.zerodhatech.models.Instrument;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import com.zerodha.service.enums.OrderEnum;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
    private final OrderService orderService;
    private final HoldingRepo holdingRepo;
    private final Logger logger = LoggerFactory.getLogger(ZerodhaInvestService.class);

    @Value("${app.user.phone-number:9145495032}")
    private String userPhoneNumber;

    // Kite Connect rejects orders whose tag exceeds 20 characters.
    private static final int KITE_MAX_TAG_LENGTH = 20;

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
            // Check if authenticated
            if (!kiteUtility.hasTokens()) {
                logger.error("Not authenticated. Please login first.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new OrderResponseDto("Not authenticated. Please login first."));
            }

            KiteConnect kiteSdk = kiteUtility.getKiteSdk();
            logger.info("KiteSdk got with tokens");

            OrderParams orderParams = getOrderParams(orderRequestDto);
            logger.info("Params set, Creating order.");
            Order order = kiteSdk.placeOrder(orderParams, orderRequestDto.variety.getCode());
            logger.info("Order Placed! Kite SDK response: {}", gson.toJson(order));

            orderRequestDto.price = Double.parseDouble(order.averagePrice);
            boolean result = orderService.saveOrder(orderRequestDto, order.orderId);

            if(result)
                logger.info("Order saved in DB");
            else
                logger.info("Failed to save in DB");

            return ResponseEntity.ok(new OrderResponseDto(order.orderId));
        }
        catch (IOException ex) {
            logger.error("IOException caught on placeOrder: msg={}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        catch (KiteException ex) {
            logger.error("KiteException caught on placeOrder: type={} code={} kiteMsg={} msg={}",
                    ex.getClass().getSimpleName(), ex.code, ex.message, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        catch (Exception ex){
            logger.error("Exception caught on placeOrder: msg={}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

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
        orderParams.product = orderRequestDto.product.getCode();
        orderParams.exchange = orderRequestDto.exchange.getCode();
        orderParams.transactionType = orderRequestDto.transactionType.getCode();
        orderParams.validity = orderRequestDto.validity.getCode();
        String tag = orderRequestDto.tag;
        if (tag != null && tag.length() > KITE_MAX_TAG_LENGTH) {
            logger.warn("Order tag '{}' exceeds Kite's {}-char limit; truncating.",
                    tag, KITE_MAX_TAG_LENGTH);
            tag = tag.substring(0, KITE_MAX_TAG_LENGTH);
        }
        orderParams.tag = tag;
        orderParams.disclosedQuantity = orderRequestDto.disclosedQuantity;
        orderParams.squareoff = orderRequestDto.squareoffValue;
        orderParams.stoploss = orderRequestDto.stoplossValue;
        orderParams.trailingStoploss = orderRequestDto.trailingStoploss;

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

    private final Gson gson = new Gson();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResponseEntity<Object> getHoldings() {
        logger.info("Fetching long term equity holdings.");
        try {
            if (!kiteUtility.hasTokens()) {
                logger.error("Not authenticated. Please login first.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(java.util.Map.of("message", "Not authenticated. Please login first at /auth/login"));
            }

            KiteConnect kiteSdk = kiteUtility.getKiteSdk();
            List<Holding> holdings = kiteSdk.getHoldings();
            logger.info("Fetched {} holdings from Kite API.", holdings != null ? holdings.size() : 0);

            // Roundtrip via Gson (used internally by Kite SDK, respects @SerializedName)
            // -> Jackson, so DTO field mapping is driven by snake_case API contract,
            // not by the SDK's Java field names (which differ across model classes).
            String json = gson.toJson(holdings);
            List<HoldingDto> dtos = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, HoldingDto.class)
            );

            persistHoldings(dtos);

            return ResponseEntity.ok(dtos);
        } catch (KiteException ex) {
            // ex.getMessage() is often null; the useful detail is ex.code and the
            // concrete subclass (TokenException = expired session, PermissionException = 403).
            logger.error("KiteException while fetching holdings: code={} type={} msg={}",
                    ex.code, ex.getClass().getSimpleName(), ex.message, ex);
            if (ex instanceof TokenException || ex instanceof PermissionException || ex.code == 403) {
                // Stored tokens are stale (Kite access tokens expire daily ~06:00 IST).
                // Clear them so subsequent hasTokens() reflects reality.
                kiteUtility.clearTokens();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(java.util.Map.of(
                                "message", "Session expired. Please login again at /auth/login",
                                "code", ex.code));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of(
                            "message", "Zerodha API error",
                            "code", ex.code,
                            "detail", ex.message != null ? ex.message : ex.getClass().getSimpleName()));
        } catch (IOException ex) {
            logger.error("IOException while fetching holdings: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("message", "Network error: " + ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching holdings: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("message", "Error: " + ex.getMessage()));
        }
    }

    private void persistHoldings(List<HoldingDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

        String phone = (userPhoneNumber != null && !userPhoneNumber.isBlank())
                ? userPhoneNumber
                : kiteUtility.getCurrentUserId();
        Instant fetchedAt = Instant.now();

        List<HoldingEntity> rows = new ArrayList<>(dtos.size());
        for (HoldingDto d : dtos) {
            rows.add(HoldingEntity.builder()
                    .phoneNumber(phone)
                    .tradingSymbol(d.getTradingSymbol())
                    .exchange(d.getExchange())
                    .instrumentToken(d.getInstrumentToken())
                    .isin(d.getIsin())
                    .product(d.getProduct())
                    .units(d.getQuantity())
                    .usedQuantity(d.getUsedQuantity())
                    .t1Quantity(d.getT1Quantity())
                    .realisedQuantity(d.getRealisedQuantity())
                    .authorisedQuantity(d.getAuthorisedQuantity())
                    .openingQuantity(d.getOpeningQuantity())
                    .collateralQuantity(d.getCollateralQuantity())
                    .collateralType(d.getCollateralType())
                    .averagePrice(d.getAveragePrice())
                    .lastPrice(d.getLastPrice())
                    .closePrice(d.getClosePrice())
                    .pnl(d.getPnl())
                    .dayChange(d.getDayChange())
                    .dayChangePercentage(d.getDayChangePercentage())
                    .fetchedAt(fetchedAt)
                    .build());
        }
        try {
            holdingRepo.saveAll(rows);
            logger.info("Persisted {} holdings for phone={} at {}.", rows.size(), phone, fetchedAt);
        } catch (Exception ex) {
            logger.error("Failed to persist holdings snapshot: {}", ex.getMessage(), ex);
        }
    }

    public HistoricalDataResponseDto getHistoricalData(HistoricalDataRequestDto request) {
        logger.info("Fetching historical data for symbol: {}, exchange: {}, from: {}, to: {}",
                request.getTradingSymbol(), request.getExchange(), request.getFromDate(), request.getToDate());

        try {
            // Check if authenticated
            if (!kiteUtility.hasTokens()) {
                logger.error("Not authenticated. Please login first.");
                return HistoricalDataResponseDto.builder()
                        .success(false)
                        .message("Not authenticated. Please login first at /auth/login")
                        .build();
            }

            // ⭐ Now getKiteSdk() returns instance with tokens already set
            KiteConnect kiteSdk = kiteUtility.getKiteSdk();
            logger.info("Using KiteConnect instance with authenticated tokens");

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

            if (e instanceof com.zerodhatech.kiteconnect.kitehttp.exceptions.PermissionException) {
                return HistoricalDataResponseDto.builder()
                        .success(false)
                        .message("Authentication error: Token expired or invalid. Please login again.")
                        .build();
            }

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

    // Rest of the methods remain the same...
    // (findInstrument, searchInstruments, getInstruments, clearInstrumentsCache,
    //  getSwingTradingData, getLatestCandle, convertToCandles)

    public InstrumentInfoDto findInstrument(String tradingSymbol, ExchangeEnum exchange) {
        logger.info("Searching for instrument: {}, exchange: {}", tradingSymbol, exchange);

        try {
            List<Instrument> instruments = getInstruments();

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
                            .strike(instrument.strike != null ? Double.valueOf(instrument.strike) : null)
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
                .continuous(true)
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
                .map(candle -> {
                    LocalDateTime timestamp = null;
                    if (candle.timeStamp != null) {
                        try {
                            timestamp = OffsetDateTime.parse(candle.timeStamp).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                        } catch (Exception e) {
                            logger.warn("Failed to parse timestamp: {}", candle.timeStamp, e);
                        }
                    }

                    return CandleData.builder().timestamp(timestamp).open(candle.open).high(candle.high).low(candle.low).close(candle.close).volume(candle.volume).openInterest(candle.oi).build();
                })
                .collect(Collectors.toList());
    }
}