package com.zerodha.service.services;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global virtual-thread executor for fan-out work (per-symbol strategy evaluations,
 * scraper calls, etc.). One virtual thread per submitted task; cheap to create.
 */
@Service
@Slf4j
public class GlobalExecutorService implements DisposableBean {

    private ExecutorService executorService;
    private final AtomicLong threadCounter = new AtomicLong();
    private volatile boolean shuttingDown = false;

    @PostConstruct
    public void init() {
        log.info("Initializing global virtual-thread executor");
        ThreadFactory factory = Thread.ofVirtual()
                .name("vt-global-", 0)
                .factory();
        executorService = Executors.newThreadPerTaskExecutor(factory);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM shutdown detected, shutting down virtual-thread executor");
            shuttingDown = true;
            shutdownExecutor();
        }));
    }

    /**
     * Submit a task on a virtual thread, naming the thread for traceability.
     * The name typically encodes the requestId + symbol so log lines (and
     * thread dumps) carry context.
     *
     * @return true if accepted, false if the executor is shutting down.
     */
    public boolean submitTask(String threadName, Runnable task) {
        if (shuttingDown) {
            log.warn("Rejecting task '{}' as executor is shutting down", threadName);
            return false;
        }
        threadCounter.incrementAndGet();
        executorService.submit(() -> {
            Thread current = Thread.currentThread();
            String original = current.getName();
            if (threadName != null && !threadName.isBlank()) {
                try {
                    current.setName(threadName);
                } catch (UnsupportedOperationException ignored) {
                    // virtual threads support setName; defensive no-op for safety
                }
            }
            try {
                task.run();
            } catch (Exception ex) {
                log.error("Unhandled error in virtual task '{}': {}", threadName, ex.getMessage(), ex);
            } finally {
                try {
                    current.setName(original);
                } catch (UnsupportedOperationException ignored) {
                }
            }
        });
        return true;
    }

    public boolean submitTask(Runnable task) {
        return submitTask(null, task);
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    @Override
    public void destroy() {
        log.info("Destroying GlobalExecutorService bean");
        shuttingDown = true;
        shutdownExecutor();
    }

    private void shutdownExecutor() {
        if (executorService == null || executorService.isShutdown()) return;
        try {
            log.info("Graceful shutdown of virtual-thread executor");
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in 60s. Forcing shutdown.");
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("Executor still did not terminate");
                }
            }
        } catch (InterruptedException e) {
            log.error("Interrupted during executor shutdown", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
