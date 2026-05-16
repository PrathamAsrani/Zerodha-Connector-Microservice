package com.zerodha.service.strategy;

import com.zerodha.service.enums.TransactionEnum;

/**
 * Output of a {@link HoldingStrategy} for one tick. {@link #hold()} signals
 * "do nothing"; {@link #trade} carries the action the orchestrator should
 * place via Kite.
 */
public record StrategyDecision(
        boolean shouldTrade,
        TransactionEnum transactionType,
        int quantity,
        double price,
        String strategyName,
        String reason
) {
    private static final StrategyDecision HOLD = new StrategyDecision(
            false, null, 0, 0.0, null, null);

    public static StrategyDecision hold() {
        return HOLD;
    }

    public static StrategyDecision trade(TransactionEnum txn, int quantity, double price,
                                         String strategyName, String reason) {
        return new StrategyDecision(true, txn, quantity, price, strategyName, reason);
    }
}
