package com.zerodha.service.strategy;

/**
 * One trading rule. Implementations should be stateless (or thread-safe) since
 * the orchestrator invokes them concurrently from per-symbol virtual threads.
 *
 * To add a new strategy: implement this interface, annotate with {@code @Component},
 * and Spring will pick it up via injection in the orchestrator. No other wiring.
 */
public interface HoldingStrategy {
    /** Stable identifier persisted on TradeEntity.strategy_name. */
    String name();

    StrategyDecision evaluate(StrategyContext context);
}
