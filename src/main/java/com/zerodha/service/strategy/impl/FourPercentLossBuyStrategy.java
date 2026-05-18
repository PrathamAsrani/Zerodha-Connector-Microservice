package com.zerodha.service.strategy.impl;

import com.zerodha.service.constants.MarketConstants;
import com.zerodha.service.enums.TransactionEnum;
import com.zerodha.service.model.StockDailySummaryEntity;
import com.zerodha.service.repository.StockDailySummaryRepo;
import com.zerodha.service.strategy.HoldingStrategy;
import com.zerodha.service.strategy.StrategyContext;
import com.zerodha.service.strategy.StrategyDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Average-down BUY: place a LIMIT buy when the unrealised loss against the
 * previous working day's summary average (falling back to the holding's
 * average price when no summary exists) exceeds the configured threshold
 * (default 4%). Order quantity is sized from the tick's net equity margin
 * snapshot — we buy as many whole units as the available net cash allows at
 * the limit price. If even one unit cannot be afforded the strategy holds.
 */
@Component
public class FourPercentLossBuyStrategy implements HoldingStrategy {

    public static final String NAME = "FOUR_PERCENT_LOSS_BUY";

    private static final Logger logger = LoggerFactory.getLogger(FourPercentLossBuyStrategy.class);

    private final double thresholdFraction;
    private final double limitDiscount;
    private final StockDailySummaryRepo stockDailySummaryRepo;

    public FourPercentLossBuyStrategy(
            @Value("${strategy.loss-buy.threshold-fraction:0.04}") double thresholdFraction,
            @Value("${strategy.loss-buy.limit-discount-rs:1.0}") double limitDiscount,
            StockDailySummaryRepo stockDailySummaryRepo) {
        this.thresholdFraction = thresholdFraction;
        this.limitDiscount = limitDiscount;
        this.stockDailySummaryRepo = stockDailySummaryRepo;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public StrategyDecision evaluate(StrategyContext ctx) {
        double avg = resolveReferencePrice(ctx);
        if (avg <= 0.0) return StrategyDecision.hold();

        double diff = ctx.ltp() - avg;
        if (diff >= 0.0) return StrategyDecision.hold();

        double lossFraction = Math.abs(diff) / avg;
        if (lossFraction <= thresholdFraction) {
            return StrategyDecision.hold();
        }

        double limitPrice = round2(Math.max(0.05, ctx.ltp() - limitDiscount));

        int affordableQty = (int) Math.floor(ctx.availableNet() / limitPrice);
        if (affordableQty < 1) {
            logger.info("{}: loss {} %% > threshold but availableNet Rs {} < limitPrice Rs {}; skipping BUY.",
                    ctx.symbol(), String.format("%.2f", lossFraction * 100.0),
                    ctx.availableNet(), limitPrice);
            return StrategyDecision.hold();
        }

        String reason = String.format(
                "loss %.2f%% > %.2f%% threshold; affordable qty %d at limit Rs %.2f (net Rs %.2f)",
                lossFraction * 100.0, thresholdFraction * 100.0,
                affordableQty, limitPrice, ctx.availableNet());

        return StrategyDecision.trade(
                TransactionEnum.BUY,
                affordableQty,
                limitPrice,
                NAME,
                reason
        );
    }

    private double resolveReferencePrice(StrategyContext ctx) {
        LocalDate prevWorkingDay = previousWorkingDay(
                ZonedDateTime.now(MarketConstants.INDIA_ZONE).toLocalDate());
        try {
            Optional<StockDailySummaryEntity> summary = stockDailySummaryRepo
                    .findByTradingSymbolAndExchangeAndSummaryDate(
                            ctx.holding().tradingSymbol(),
                            ctx.holding().exchange(),
                            prevWorkingDay);
            if (summary.isPresent() && summary.get().getAvgPrice() > 0.0) {
                return summary.get().getAvgPrice();
            }
        } catch (Exception ex) {
            logger.warn("{}: failed to load daily summary for {}; falling back to holding avg. ({})",
                    ctx.symbol(), prevWorkingDay, ex.getMessage());
        }
        return ctx.holding().averagePrice();
    }

    private static LocalDate previousWorkingDay(LocalDate from) {
        LocalDate d = from.minusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY
                || d.getDayOfWeek() == DayOfWeek.SUNDAY
                || MarketConstants.NSE_HOLIDAYS.contains(d)) {
            d = d.minusDays(1);
        }
        return d;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
