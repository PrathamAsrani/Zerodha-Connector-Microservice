package com.zerodha.service.services;

import com.zerodha.service.constants.MarketConstants;
import com.zerodha.service.dtos.scraper.ScreenerLtpDto;
import com.zerodha.service.model.StockDailySummaryEntity;
import com.zerodha.service.model.StockLtpEntity;
import com.zerodha.service.model.StockMasterEntity;
import com.zerodha.service.repository.StockDailySummaryRepo;
import com.zerodha.service.repository.StockLtpRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;

/**
 * Drives the per-minute LTP fetch and the 15:25 IST daily-summary roll-up.
 *
 * Service lifecycle (deployed via GCP Cloud Scheduler at Mon-Fri 09:30 IST):
 *   1. ApplicationReadyEvent -> if market is closed, exit the JVM to save resources.
 *      If open, populate the in-memory stock cache once.
 *   2. Cron every minute Mon-Fri 09-15 IST -> scrape LTP per symbol from screener.in
 *      and persist each as a StockLtpEntity row with timestamp.
 *   3. Cron 15:25 IST Mon-Fri -> compute avg/min/max/last for the current day and
 *      upsert into stock_daily_summary.
 */
@Service
public class StockDataSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(StockDataSchedulerService.class);
    private static final String IST = "Asia/Kolkata";

    private final StockCacheService stockCacheService;
    private final StockLtpRepo stockLtpRepo;
    private final StockDailySummaryRepo stockDailySummaryRepo;
    private final ScreenerScraperService screenerScraperService;
    private final ConfigurableApplicationContext applicationContext;
    private final boolean exitWhenMarketClosed;

    public StockDataSchedulerService(
            StockCacheService stockCacheService,
            StockLtpRepo stockLtpRepo,
            StockDailySummaryRepo stockDailySummaryRepo,
            ScreenerScraperService screenerScraperService,
            ConfigurableApplicationContext applicationContext,
            @Value("${stock.scheduler.exit-when-market-closed:true}") boolean exitWhenMarketClosed) {
        this.stockCacheService = stockCacheService;
        this.stockLtpRepo = stockLtpRepo;
        this.stockDailySummaryRepo = stockDailySummaryRepo;
        this.screenerScraperService = screenerScraperService;
        this.applicationContext = applicationContext;
        this.exitWhenMarketClosed = exitWhenMarketClosed;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!MarketConstants.IS_MARKET_OPEN()) {
            logger.warn("Market is closed at startup. Exiting service to conserve resources.");
            if (exitWhenMarketClosed) {
                shutdownAsync();
            }
            return;
        }
        logger.info("Market is open. Loading stock cache for today's session.");
        stockCacheService.loadIfNeeded();
    }

    @Scheduled(cron = "0 * 9-15 * * MON-FRI", zone = IST)
    @Transactional
    public void fetchAndPersistLtp() {
        if (!MarketConstants.IS_MARKET_OPEN()) {
            return;
        }
        if (!stockCacheService.isLoaded()) {
            stockCacheService.loadIfNeeded();
        }
        List<StockMasterEntity> stocks = stockCacheService.getStocks();
        if (stocks.isEmpty()) {
            logger.debug("No active stocks in cache; skipping LTP fetch.");
            return;
        }

        Instant fetchedAt = Instant.now();
        List<StockLtpEntity> rows = new ArrayList<>(stocks.size());
        int failed = 0;

        for (StockMasterEntity stock : stocks) {
            try {
                ScreenerLtpDto dto = screenerScraperService.fetchLtp(stock.getTradingSymbol());
                if (dto == null || dto.getLastPrice() == null) {
                    failed++;
                    continue;
                }
                rows.add(StockLtpEntity.builder()
                        .tradingSymbol(stock.getTradingSymbol())
                        .exchange(stock.getExchange())
                        .instrumentToken(stock.getInstrumentToken())
                        .ltp(dto.getLastPrice())
                        .fetchedAt(fetchedAt)
                        .build());
            } catch (Exception ex) {
                failed++;
                logger.warn("Screener LTP fetch failed for {}: {}", stock.getTradingSymbol(), ex.getMessage());
            }
        }

        if (!rows.isEmpty()) {
            stockLtpRepo.saveAll(rows);
        }
        logger.info("Persisted {} LTP rows at {} ({} failed).", rows.size(), fetchedAt, failed);
    }

    @Scheduled(cron = "0 25 15 * * MON-FRI", zone = IST)
    @Transactional
    public void updateDailySummary() {
        LocalDate today = ZonedDateTime.now(MarketConstants.INDIA_ZONE).toLocalDate();
        Instant startOfDay = today.atStartOfDay(MarketConstants.INDIA_ZONE).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(MarketConstants.INDIA_ZONE).toInstant();

        List<StockMasterEntity> stocks = stockCacheService.getStocks();
        if (stocks.isEmpty()) {
            logger.warn("No stocks in cache; skipping daily summary roll-up for {}.", today);
            return;
        }

        Instant now = Instant.now();
        int updated = 0;
        for (StockMasterEntity stock : stocks) {
            List<StockLtpEntity> ltps = stockLtpRepo
                    .findByTradingSymbolAndExchangeAndFetchedAtBetweenOrderByFetchedAtAsc(
                            stock.getTradingSymbol(), stock.getExchange(), startOfDay, endOfDay);
            if (ltps.isEmpty()) continue;

            DoubleSummaryStatistics stats = ltps.stream()
                    .mapToDouble(StockLtpEntity::getLtp)
                    .summaryStatistics();
            double lastPrice = ltps.get(ltps.size() - 1).getLtp();

            StockDailySummaryEntity summary = stockDailySummaryRepo
                    .findByTradingSymbolAndExchangeAndSummaryDate(
                            stock.getTradingSymbol(), stock.getExchange(), today)
                    .orElseGet(() -> StockDailySummaryEntity.builder()
                            .tradingSymbol(stock.getTradingSymbol())
                            .exchange(stock.getExchange())
                            .summaryDate(today)
                            .build());

            summary.setAvgPrice(stats.getAverage());
            summary.setMinPrice(stats.getMin());
            summary.setMaxPrice(stats.getMax());
            summary.setLastPrice(lastPrice);
            summary.setSampleCount((int) stats.getCount());
            summary.setUpdatedAt(now);
            stockDailySummaryRepo.save(summary);
            updated++;
        }

        logger.info("Daily summary updated for {} stocks on {}.", updated, today);
    }

    private void shutdownAsync() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }, "stock-scheduler-shutdown");
        t.setDaemon(false);
        t.start();
    }
}
