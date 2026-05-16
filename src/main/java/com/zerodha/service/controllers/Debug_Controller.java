package com.zerodha.service.controllers;

import com.zerodha.service.services.HoldingStrategyOrchestrator;
import com.zerodha.service.services.MailService;
import com.zerodha.service.utils.MDCUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Manual triggers for verifying outbound mail wiring without waiting for the
 * 5-min cron. Remove before exposing the service publicly.
 */
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
@Slf4j
public class Debug_Controller {

    private final MailService mailService;
    private final HoldingStrategyOrchestrator holdingStrategyOrchestrator;

    /**
     * Fires the same "service is alive" mail the first cron tick would fire,
     * with the same one-shot guard bypassed via a direct call to MailService.
     * Use this to confirm SMTP credentials / app password are correct.
     */
    @PostMapping("/mail/startup")
    public ResponseEntity<Map<String, Object>> testStartupMail() {
        String requestId = "debug-" + MDCUtil.getShortId();
        MDCUtil.setRequestId(requestId);
        try {
            log.info("Manual startup-mail test triggered (requestId={})", requestId);
            mailService.sendStartupAlert(0, 1, requestId);
            return ResponseEntity.ok(Map.of(
                    "status", "submitted",
                    "requestId", requestId,
                    "hint", "check logs for 'Alert mail sent' or 'Mail not configured' / 'Failed to send'"));
        } finally {
            MDCUtil.clear();
        }
    }

    /**
     * Fires a fake sell alert with caller-supplied values so you can preview
     * the formatted HTML and confirm Gmail filtering / inbox routing.
     */
    @PostMapping("/mail/sell-alert")
    public ResponseEntity<Map<String, Object>> testSellAlert(
            @RequestParam(defaultValue = "RELIANCE") String symbol,
            @RequestParam(defaultValue = "10") int quantity,
            @RequestParam(defaultValue = "2500") double avgPrice,
            @RequestParam(defaultValue = "2625") double ltp) {
        String requestId = "debug-" + MDCUtil.getShortId();
        MDCUtil.setRequestId(requestId);
        try {
            double target = Math.round((ltp + 1.0) * 100.0) / 100.0;
            mailService.sendSellTargetAlert(
                    symbol, quantity, avgPrice, ltp, target,
                    "DEBUG_TEST", "FAKE-ORDER-ID", requestId);
            return ResponseEntity.ok(Map.of(
                    "status", "submitted",
                    "requestId", requestId,
                    "symbol", symbol,
                    "target", target));
        } finally {
            MDCUtil.clear();
        }
    }

    /**
     * Fires the scrape-failure mail variant.
     */
    @PostMapping("/mail/scrape-failure")
    public ResponseEntity<Map<String, Object>> testScrapeFailureMail(
            @RequestParam(defaultValue = "RELIANCE") String symbol,
            @RequestParam(defaultValue = "Manual debug trigger") String error) {
        String requestId = "debug-" + MDCUtil.getShortId();
        MDCUtil.setRequestId(requestId);
        try {
            mailService.sendScrapeFailureAlert(symbol, error, requestId);
            return ResponseEntity.ok(Map.of("status", "submitted", "requestId", requestId));
        } finally {
            MDCUtil.clear();
        }
    }

    /**
     * Resets the orchestrator's one-shot guard so the next real cron tick will
     * send a fresh startup mail. Useful after redeploys without a JVM restart.
     */
    @PostMapping("/mail/reset-startup-guard")
    public ResponseEntity<Map<String, Object>> resetStartupGuard() {
        holdingStrategyOrchestrator.resetStartupAlertGuard();
        return ResponseEntity.ok(Map.of("status", "reset"));
    }
}
