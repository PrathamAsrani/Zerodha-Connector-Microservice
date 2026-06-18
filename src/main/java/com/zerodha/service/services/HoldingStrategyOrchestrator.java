package com.zerodha.service.services;

import com.zerodha.service.dtos.order.OrderRequestDto;
import com.zerodha.service.dtos.order.OrderResponseDto;
import com.zerodha.service.dtos.scraper.ScreenerLtpDto;
import com.zerodha.service.enums.ExchangeEnum;
import com.zerodha.service.enums.OrderEnum;
import com.zerodha.service.enums.ProductEnum;
import com.zerodha.service.enums.TransactionEnum;
import com.zerodha.service.enums.ValidityEnum;
import com.zerodha.service.enums.VarietyEnum;
import com.zerodha.service.model.TradeEntity;
import com.zerodha.service.repository.TradeRepo;
import com.zerodha.service.services.HoldingCacheService.CachedHolding;
import com.zerodha.service.strategy.HoldingStrategy;
import com.zerodha.service.strategy.StrategyContext;
import com.zerodha.service.strategy.StrategyDecision;
import com.zerodha.service.utils.MDCUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Per-tick fan-out: one virtual thread per cached holding. Each thread scrapes
 * the LTP, runs every registered {@link HoldingStrategy}, and (if any of them
 * decides to trade) places the order via {@link ZerodhaInvestService} guarded
 * by a 5-min idempotency reservation on {@link HoldingCacheService}.
 */
@Service
public class HoldingStrategyOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(HoldingStrategyOrchestrator.class);

    private final HoldingCacheService holdingCacheService;
    private final ScreenerScraperService scraper;
    private final GlobalExecutorService executor;
    private final MailService mailService;
    private final ZerodhaInvestService zerodhaInvestService;
    private final ZerodhaUserService zerodhaUserService;
    private final PortfolioAllocationService portfolioAllocationService;
    private final TradeRepo tradeRepo;
    private final List<HoldingStrategy> strategies;
    private final String phoneNumber;

    // One-shot guard: send the "service is alive" mail on the first cron tick
    // after JVM boot only. Reset across restarts, not across trading days.
    private volatile boolean startupAlertSent = false;

    private static List<String> IGNORED_HOLDINGS = Arrays.asList(
            "GOLDCASE"
    );

    public HoldingStrategyOrchestrator(
            HoldingCacheService holdingCacheService,
            ScreenerScraperService scraper,
            GlobalExecutorService executor,
            MailService mailService,
            ZerodhaInvestService zerodhaInvestService,
            ZerodhaUserService zerodhaUserService,
            PortfolioAllocationService portfolioAllocationService,
            TradeRepo tradeRepo,
            List<HoldingStrategy> strategies,
            @Value("${app.user.phone-number:}") String phoneNumber) {
        this.holdingCacheService = holdingCacheService;
        this.scraper = scraper;
        this.executor = executor;
        this.mailService = mailService;
        this.zerodhaInvestService = zerodhaInvestService;
        this.zerodhaUserService = zerodhaUserService;
        this.portfolioAllocationService = portfolioAllocationService;
        this.tradeRepo = tradeRepo;
        this.strategies = strategies;
        this.phoneNumber = phoneNumber;
        logger.info("HoldingStrategyOrchestrator loaded with {} strategies: {}",
                strategies.size(), strategies.stream().map(HoldingStrategy::name).toList());
    }

    /**
     * Fire the startup alert mail once per JVM lifecycle. Safe to call from
     * every cron tick; subsequent invocations are no-ops.
     */
    public void sendStartupAlertOnce(String requestId) {
        if (startupAlertSent) return;
        synchronized (this) {
            if (startupAlertSent) return;
            startupAlertSent = true;
        }
        mailService.sendStartupAlert(
                holdingCacheService.getHoldings().size(),
                strategies.size(),
                requestId
        );
    }

    /** Allow debug endpoint to clear the guard so the next cron tick re-sends. */
    public void resetStartupAlertGuard() {
        startupAlertSent = false;
    }

    public void runForAllHoldings(String requestId) {
        List<CachedHolding> holdings = holdingCacheService.getHoldings();
        if (holdings.isEmpty()) {
            logger.info("No cached holdings; skipping strategy fan-out for request {}.", requestId);
            holdingCacheService.reload();
            return;
        }
        // Snapshot the equity net margin once per tick so BUY strategies can size
        // their orders. All per-symbol virtual threads share this snapshot.
        double availableNet = zerodhaUserService.getNetMargin("equity");
        logger.info("Tick {} margin snapshot: equity net = Rs {}", requestId, availableNet);
        for (CachedHolding h : holdings) {
            if(IGNORED_HOLDINGS.contains(h.tradingSymbol()))
                continue;
            String threadName = requestId + "_" + h.tradingSymbol() + "_HOLDING";
            Runnable task = MDCUtil.wrapWithMdc(() -> evaluateOne(h, requestId, availableNet));
            executor.submitTask(threadName, task);
        }
        logger.info("Submitted {} per-holding strategy tasks for request {}.", holdings.size(), requestId);
    }

    private void evaluateOne(CachedHolding holding, String requestId, double availableNet) {
        double ltp;
        try {
            ScreenerLtpDto dto = scraper.fetchLtp(holding.tradingSymbol());
            if (dto == null || dto.getLastPrice() == null) {
                throw new IllegalStateException("Scraper returned no last price");
            }
            ltp = dto.getLastPrice();
        } catch (Exception ex) {
            logger.error("LTP scrape failed for {}: {}", holding.tradingSymbol(), ex.getMessage(), ex);
            mailService.sendScrapeFailureAlert(holding.tradingSymbol(), ex.getMessage(), requestId);
            return;
        }

        ExchangeEnum exchange = parseExchange(holding.exchange());
        StrategyContext ctx = new StrategyContext(
                holding, exchange, ltp, Instant.now(), requestId, phoneNumber, availableNet);

        for (HoldingStrategy strategy : strategies) {
            StrategyDecision decision;
            try {
                decision = strategy.evaluate(ctx);
            } catch (Exception ex) {
                logger.error("Strategy {} threw for symbol {}: {}",
                        strategy.name(), holding.tradingSymbol(), ex.getMessage(), ex);
                continue;
            }
            if (decision == null || !decision.shouldTrade()) continue;
            executeTrade(ctx, decision);
        }
    }

    private void executeTrade(StrategyContext ctx, StrategyDecision decision) {
        boolean isBuy = decision.transactionType() == TransactionEnum.BUY;

        // Per-stock concentration guard. Pre-check here so a blocked BUY skips
        // quietly (no order, no reservation, no "FAILED" alert). placeOrder() also
        // enforces this as a backstop for the direct controller path.
        if (isBuy) {
            double buyValue = decision.price() * decision.quantity();
            if (!portfolioAllocationService.isBuyWithinAllocationLimit(ctx.symbol(), buyValue)) {
                logger.info("Skipping BUY for {} qty {} @ Rs{}: per-stock allocation cap reached.",
                        ctx.symbol(), decision.quantity(), decision.price());
                return;
            }
        }

        String reservationKey = ctx.symbol() + "-" + decision.transactionType()
                + "-" + decision.quantity() + "-success";
        if (!holdingCacheService.tryReserve(reservationKey)) {
            logger.info("Skipping {} for {} qty {}: reservation key '{}' already held within TTL.",
                    decision.transactionType(), ctx.symbol(), decision.quantity(), reservationKey);
            return;
        }

        OrderRequestDto req = new OrderRequestDto(
                ctx.symbol(),
                ctx.exchange(),
                decision.transactionType(),
                OrderEnum.LIMIT,
                decision.quantity(),
                ProductEnum.CNC,
                decision.price(),
                0.0,
                0,
                ValidityEnum.DAY,
                null,
                null,
                null,
                VarietyEnum.REGULAR,
                decision.strategyName()
        );

        String orderId = null;
        boolean placed = false;
        try {
            ResponseEntity<OrderResponseDto> resp = zerodhaInvestService.placeOrder(req);
            if (resp != null && resp.getBody() != null) {
                orderId = resp.getBody().getOrderId();
            }
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalStateException(
                        "placeOrder returned no orderId (status=" + (resp == null ? "null" : resp.getStatusCode()) + ")");
            }
            placed = true;
            logger.info("Placed {} order id={} for {} qty={} @ Rs{} via {}",
                    decision.transactionType(), orderId, ctx.symbol(),
                    decision.quantity(), decision.price(), decision.strategyName());
        } catch (Exception ex) {
            logger.error("Order placement failed for {} {} qty={} @ Rs{}: {}",
                    decision.transactionType(), ctx.symbol(),
                    decision.quantity(), decision.price(), ex.getMessage(), ex);
            mailService.sendOrderFailureAlert(
                    ctx.symbol(), decision.transactionType().getCode(),
                    decision.quantity(), decision.price(), ex.getMessage(), ctx.requestId());
        }

        if (placed) {
            double profit = (decision.price() - ctx.holding().averagePrice()) * decision.quantity();
            try {
                tradeRepo.save(TradeEntity.builder()
                        .tradingSymbol(ctx.symbol())
                        .exchange(ctx.holding().exchange())
                        .avgPrice(ctx.holding().averagePrice())
                        .targetPrice(decision.price())
                        .profit(profit)
                        .quantity(decision.quantity())
                        .tradeTime(Instant.now())
                        .phoneNumber(ctx.phoneNumber())
                        .transactionType(decision.transactionType())
                        .strategyName(decision.strategyName())
                        .orderId(orderId)
                        .requestId(ctx.requestId())
                        .build());
            } catch (Exception ex) {
                logger.error("Failed to persist TradeEntity for {} (orderId={}): {}",
                        ctx.symbol(), orderId, ex.getMessage(), ex);
            }
        }

        if (decision.transactionType() == TransactionEnum.SELL) {
            mailService.sendSellTargetAlert(
                    ctx.symbol(),
                    decision.quantity(),
                    ctx.holding().averagePrice(),
                    ctx.ltp(),
                    decision.price(),
                    decision.strategyName(),
                    orderId,
                    ctx.requestId()
            );
        } else if (isBuy) {
            mailService.sendBuyTargetAlert(
                    ctx.symbol(),
                    decision.quantity(),
                    ctx.holding().averagePrice(),
                    ctx.ltp(),
                    decision.price(),
                    decision.strategyName(),
                    orderId,
                    ctx.requestId()
            );
        }
    }

    private static ExchangeEnum parseExchange(String raw) {
        if (raw == null || raw.isBlank()) return ExchangeEnum.NSE;
        try {
            return ExchangeEnum.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ExchangeEnum.NSE;
        }
    }
}
