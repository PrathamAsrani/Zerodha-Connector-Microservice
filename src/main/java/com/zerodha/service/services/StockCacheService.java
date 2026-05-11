package com.zerodha.service.services;

import com.zerodha.service.model.StockMasterEntity;
import com.zerodha.service.repository.StockMasterRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the active list of stocks in process memory so the per-minute LTP
 * fetcher does not hit the DB on every tick. Loaded once at startup and
 * refreshable explicitly via {@link #reload()}.
 */
@Service
public class StockCacheService {

    private static final Logger logger = LoggerFactory.getLogger(StockCacheService.class);

    private final StockMasterRepo stockMasterRepo;
    private final AtomicReference<List<StockMasterEntity>> cache =
            new AtomicReference<>(Collections.emptyList());
    private volatile boolean loaded = false;

    public StockCacheService(StockMasterRepo stockMasterRepo) {
        this.stockMasterRepo = stockMasterRepo;
    }

    public synchronized void loadIfNeeded() {
        if (loaded) return;
        reload();
    }

    public synchronized void reload() {
        List<StockMasterEntity> stocks = stockMasterRepo.findByActiveTrue();
        cache.set(List.copyOf(stocks));
        loaded = true;
        logger.info("Stock cache loaded with {} active stocks.", stocks.size());
    }

    public List<StockMasterEntity> getStocks() {
        return cache.get();
    }

    public boolean isLoaded() {
        return loaded;
    }
}
