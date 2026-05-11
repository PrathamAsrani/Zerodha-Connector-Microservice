package com.zerodha.service.services;

import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Tick;
import com.zerodhatech.ticker.KiteTicker;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TickerService {
    private static final Logger logger = LoggerFactory.getLogger(TickerService.class);
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long DEFAULT_TICK_TIMEOUT_MS = 5_000;

    private final KiteUtility kiteUtility;
    private final String apiKey;

    private volatile KiteTicker ticker;
    private final Map<Long, Double> ltpCache = new ConcurrentHashMap<>();
    private final Set<Long> subscribedTokens = ConcurrentHashMap.newKeySet();
    private final Map<Long, CompletableFuture<Double>> pendingFirstTick = new ConcurrentHashMap<>();

    public TickerService(KiteUtility kiteUtility, @Value("${ZERODHA_API_KEY}") String apiKey) {
        this.kiteUtility = kiteUtility;
        this.apiKey = apiKey;
    }

    public Double getLtp(long instrumentToken) throws Exception {
        return getLtp(instrumentToken, DEFAULT_TICK_TIMEOUT_MS);
    }

    public Double getLtp(long instrumentToken, long timeoutMs) throws Exception {
        ensureConnected();

        Double cached = ltpCache.get(instrumentToken);
        if (cached != null) return cached;

        // Register the waiter before subscribing, so we can't miss the first tick.
        CompletableFuture<Double> future = pendingFirstTick.computeIfAbsent(
                instrumentToken, k -> new CompletableFuture<>()
        );

        if (subscribedTokens.add(instrumentToken)) {
            ArrayList<Long> tokens = new ArrayList<>();
            tokens.add(instrumentToken);
            logger.info("Subscribing to instrument token {} on KiteTicker", instrumentToken);
            ticker.subscribe(tokens);
            ticker.setMode(tokens, KiteTicker.modeLTP);
        }

        // Re-check cache: a tick may have arrived between the first lookup and subscribe.
        Double again = ltpCache.get(instrumentToken);
        if (again != null) {
            pendingFirstTick.remove(instrumentToken);
            return again;
        }

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            pendingFirstTick.remove(instrumentToken);
            throw ex;
        }
    }

    public Double getCachedLtp(long instrumentToken) {
        return ltpCache.get(instrumentToken);
    }

    public boolean isConnected() {
        return ticker != null && ticker.isConnectionOpen();
    }

    private synchronized void ensureConnected() {
        if (ticker != null && ticker.isConnectionOpen()) return;

        if (!kiteUtility.hasTokens()) {
            throw new IllegalStateException("Not authenticated. Please login first at /auth/login");
        }

        KiteConnect kite = kiteUtility.getKiteSdk();
        String accessToken = kite.getAccessToken();

        validateCredentials(accessToken);

        // Pre-emptively tear down any previous (likely stale/failed) ticker so reconnect
        // attempts from an old auth attempt don't keep firing.
        if (ticker != null) {
            try { ticker.disconnect(); } catch (Exception ignored) {}
        }

        logger.info("Initializing KiteTicker (apiKey={}, accessToken={})",
                mask(apiKey), mask(accessToken));

        ticker = new KiteTicker(accessToken, apiKey);
        // Don't auto-reconnect — if the first handshake fails (403), retrying won't help
        // and it floods the logs. We'll reconnect on the next /invest/ltp call instead.
        ticker.setTryReconnection(false);

        CountDownLatch connectedLatch = new CountDownLatch(1);

        ticker.setOnTickerArrivalListener(this::onTicks);
        ticker.setOnConnectedListener(() -> {
            onConnected();
            connectedLatch.countDown();
        });
        ticker.setOnDisconnectedListener(this::onDisconnected);
        ticker.setOnErrorListener(new com.zerodhatech.ticker.OnError() {
            @Override
            public void onError(Exception exception) {
                logger.error("KiteTicker error: {}", exception.getMessage());
            }
            @Override
            public void onError(com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException kiteException) {
                logger.error("KiteTicker KiteException: {}", kiteException.getMessage());
            }
            @Override
            public void onError(String error) {
                logger.error("KiteTicker error string: {}", error);
            }
        });

        logger.info("Connecting to Kite WebSocket...");
        ticker.connect();

        try {
            if (!connectedLatch.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("WebSocket did not connect within {}s. Likely cause: invalid api_key or expired access_token. " +
                        "Verify ZERODHA_API_KEY matches the key used at login and that the access_token has not expired " +
                        "(Kite tokens expire daily around 06:00 IST).", CONNECT_TIMEOUT_SECONDS);
                throw new IllegalStateException("WebSocket connection failed (likely 403). Check ZERODHA_API_KEY and re-login at /auth/login.");
            }
            logger.info("WebSocket connection established.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while connecting to WebSocket", e);
        }
    }

    private void validateCredentials(String accessToken) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ZERODHA_API_KEY is not configured.");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Access token is missing on the KiteConnect SDK. Please re-login at /auth/login.");
        }
    }

    private static String mask(String value) {
        if (value == null) return "<null>";
        int len = value.length();
        if (len <= 4) return "****(len=" + len + ")";
        return value.substring(0, 2) + "***" + value.substring(len - 2) + "(len=" + len + ")";
    }

    private void onTicks(ArrayList<Tick> ticks) {
        for (Tick tick : ticks) {
            long token = tick.getInstrumentToken();
            double ltp = tick.getLastTradedPrice();
            ltpCache.put(token, ltp);

            CompletableFuture<Double> waiter = pendingFirstTick.remove(token);
            if (waiter != null && !waiter.isDone()) {
                waiter.complete(ltp);
            }
        }
    }

    private void onConnected() {
        logger.info("WebSocket connected. Resubscribing {} tokens.", subscribedTokens.size());
        if (!subscribedTokens.isEmpty()) {
            ArrayList<Long> tokens = new ArrayList<>(subscribedTokens);
            ticker.subscribe(tokens);
            ticker.setMode(tokens, KiteTicker.modeLTP);
        }
    }

    private void onDisconnected() {
        logger.warn("WebSocket disconnected.");
    }

    @PreDestroy
    public void shutdown() {
        if (ticker != null && ticker.isConnectionOpen()) {
            logger.info("Disconnecting KiteTicker on shutdown.");
            try { ticker.disconnect(); } catch (Exception ignored) {}
        }
    }
}
