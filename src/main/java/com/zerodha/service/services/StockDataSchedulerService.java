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
 * Drives the every-5-minute LTP fetch and the 15:30 IST daily-summary roll-up.
 *
 * Each trading day the 5-min cron is invoked with a per-day tick index
 * (0, 1, 2, ...) starting at 0 for the 09:30 IST run. The index resets when
 * the IST calendar date changes. The index-0 tick is responsible for the
 * "first run of the day" work — verifying the market is open and warming
 * the in-memory StockMaster cache before any LTP scraping happens.
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

    @Value("${application.shutdown:false}")
    private boolean shutdown;

    private LocalDate currentTickDate;
    private int tickIndex;

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
            logger.warn("Market is closed at startup. Skipping cache load.");
            if (exitWhenMarketClosed && shutdown) {
                shutdownAsync();
            }
            return;
        }
        logger.info("Market is open. Loading stock cache for today's session.");
        stockCacheService.loadIfNeeded();
    }

    /**
     * Every 5 minutes Mon-Fri between 09:30 and 15:30 IST. The cron is widened
     * to 9-15 hour range; IS_MARKET_OPEN() filters out the 9:00-9:29 and
     * 15:31-15:55 fringe minutes.
     */
    @Scheduled(cron = "0 0/5 9-15 * * MON-FRI", zone = IST)
    @Transactional
    public void fetchAndPersistLtp() {
        if (!MarketConstants.IS_MARKET_OPEN()) {
            return;
        }

        int index = nextTickIndex();

        if (index == 0) {
            logger.info("First tick of the day (index 0). Warming stock cache.");
            stockCacheService.reload();
        } else if (!stockCacheService.isLoaded()) {
            stockCacheService.loadIfNeeded();
        }

        List<StockMasterEntity> stocks = stockCacheService.getStocks();
        if (stocks.isEmpty()) {
            logger.debug("No active stocks in cache; skipping LTP fetch at tick {}.", index);
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
        logger.info("Tick {} | persisted {} LTP rows at {} ({} failed).",
                index, rows.size(), fetchedAt, failed);
    }

    private synchronized int nextTickIndex() {
        LocalDate today = ZonedDateTime.now(MarketConstants.INDIA_ZONE).toLocalDate();
        if (currentTickDate == null || !currentTickDate.equals(today)) {
            currentTickDate = today;
            tickIndex = 0;
            return 0;
        }
        return ++tickIndex;
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = IST)
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
