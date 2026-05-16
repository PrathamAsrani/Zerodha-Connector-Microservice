package com.zerodha.service.strategy.impl;

import com.zerodha.service.enums.TransactionEnum;
import com.zerodha.service.strategy.HoldingStrategy;
import com.zerodha.service.strategy.StrategyContext;
import com.zerodha.service.strategy.StrategyDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sell the entire holding when unrealised gain exceeds the configured
 * threshold (default 4%). Limit price = current LTP + 1 Re.
 */
@Component
public class FourPercentProfitSellStrategy implements HoldingStrategy {

    public static final String NAME = "FOUR_PERCENT_PROFIT_SELL";

    private final double thresholdFraction;
    private final double limitPremium;

    public FourPercentProfitSellStrategy(
            @Value("${strategy.profit-sell.threshold-fraction:0.04}") double thresholdFraction,
            @Value("${strategy.profit-sell.limit-premium-rs:1.0}") double limitPremium) {
        this.thresholdFraction = thresholdFraction;
        this.limitPremium = limitPremium;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public StrategyDecision evaluate(StrategyContext ctx) {
        double avg = ctx.holding().averagePrice();
        if (avg <= 0.0) return StrategyDecision.hold();

        double gain = (ctx.ltp() - avg) / avg;
        if (gain <= thresholdFraction) {
            return StrategyDecision.hold();
        }

        double limitPrice = round2(ctx.ltp() + limitPremium);
        String reason = String.format("gain %.2f%% > %.2f%% threshold",
                gain * 100.0, thresholdFraction * 100.0);
        return StrategyDecision.trade(
                TransactionEnum.SELL,
                ctx.holding().quantity(),
                limitPrice,
                NAME,
                reason
        );
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
