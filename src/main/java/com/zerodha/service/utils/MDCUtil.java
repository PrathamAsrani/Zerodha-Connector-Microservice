package com.zerodha.service.utils;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

public class MDCUtil {

    public static String X_REQUEST_ID = "X-REQUEST-ID";
    public static String DEBUG_REQUEST_ID = "DEBUG-ID";
    public static String USER_ID = "USER-ID";

    public static void setRequestId(String xRequestId) {
        if (xRequestId != null) {
            MDC.put(X_REQUEST_ID, xRequestId);
        }
    }

    public static void setDebugRequestId(String debugRequestId) {
        if (debugRequestId != null) {
            MDC.put(DEBUG_REQUEST_ID, debugRequestId);
        }
    }

    public static String getRequestId() {
        return MDC.get(X_REQUEST_ID);
    }

    public static void setUserId(String userId) {
        if (userId != null) {
            MDC.put(USER_ID, userId);
        }
    }

    public static String getUserId() {
        return MDC.get(USER_ID);
    }

    public static void clear() {
        MDC.clear();
    }

    public static String getShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // Wrap a Runnable so the captured MDC context is restored on the executing
    // thread (virtual or kafka worker). MDC is per-thread, so without this the
    // requestId from the submitting thread is lost.
    public static Runnable wrapWithMdc(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            if (contextMap != null) MDC.setContextMap(contextMap);
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
