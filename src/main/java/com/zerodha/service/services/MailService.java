package com.zerodha.service.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Outbound alerts for the trading strategies. Wraps Spring's JavaMailSender so
 * the orchestrator and per-symbol virtual threads can fire mails without
 * touching SMTP details. Failures are logged but never thrown — alerts must
 * not break the trading flow.
 */
@Service
@Slf4j
public class MailService {

    private static final DateTimeFormatter IST_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.of("Asia/Kolkata"));

    private final JavaMailSender mailSender;
    private final String from;
    private final String alertTo;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.from:}") String from,
                       @Value("${app.mail.alert-to:prathamasrani.cs@gmail.com}") String alertTo) {
        this.mailSender = mailSender;
        this.from = from;
        this.alertTo = alertTo;
    }

    public void sendSellTargetAlert(String symbol, int quantity, double avgPrice,
                                    double ltp, double targetPrice, String strategyName,
                                    String orderId, String requestId) {
        double gainPct = ((ltp - avgPrice) / avgPrice) * 100.0;
        double profit = (targetPrice - avgPrice) * quantity;
        String subject = String.format(
                "[ACTION] SELL %s @ +%.2f%% — target Rs %.2f (qty %d) [%s]",
                symbol, gainPct, targetPrice, quantity, strategyName);

        String html = """
                <html><body style="font-family: -apple-system, Helvetica, Arial, sans-serif;">
                <h2 style="color:#c0392b; margin:0 0 12px 0;">SELL Triggered: %s</h2>
                <p style="margin:0 0 8px 0;"><b>Strategy:</b> %s</p>
                <p style="margin:0 0 8px 0;"><b>Order ID:</b> %s</p>
                <table cellpadding="6" cellspacing="0" border="1" style="border-collapse:collapse; border-color:#ccc;">
                  <tr><th align="left">Symbol</th><td>%s</td></tr>
                  <tr><th align="left">Quantity</th><td>%d</td></tr>
                  <tr><th align="left">Avg buy price</th><td>Rs %.2f</td></tr>
                  <tr><th align="left">Current LTP</th><td>Rs %.2f</td></tr>
                  <tr><th align="left">Target (LTP + 1)</th><td>Rs %.2f</td></tr>
                  <tr><th align="left">Unrealised gain</th><td>+%.2f%%</td></tr>
                  <tr><th align="left">Expected profit at target</th><td>Rs %.2f</td></tr>
                </table>
                <p style="margin-top:14px; color:#555;">
                  Request: <code>%s</code><br/>
                  Time: %s
                </p>
                </body></html>
                """.formatted(
                        symbol, strategyName, orderId == null ? "(pending)" : orderId,
                        symbol, quantity, avgPrice, ltp, targetPrice, gainPct, profit,
                        requestId == null ? "(none)" : requestId,
                        IST_TS.format(ZonedDateTime.now())
                );

        sendHtml(subject, html);
    }

    public void sendBuyTargetAlert(String symbol, int quantity, double avgPrice,
                                   double ltp, double targetPrice, String strategyName,
                                   String orderId, String requestId) {
        double lossPct = ((avgPrice - ltp) / avgPrice) * 100.0;
        double addedCost = targetPrice * quantity;
        String subject = String.format(
                "[ACTION] BUY %s @ -%.2f%% — target Rs %.2f (qty %d) [%s]",
                symbol, lossPct, targetPrice, quantity, strategyName);

        String html = """
                <html><body style="font-family: -apple-system, Helvetica, Arial, sans-serif;">
                <h2 style="color:#1f6feb; margin:0 0 12px 0;">BUY Triggered: %s</h2>
                <p style="margin:0 0 8px 0;"><b>Strategy:</b> %s</p>
                <p style="margin:0 0 8px 0;"><b>Order ID:</b> %s</p>
                <table cellpadding="6" cellspacing="0" border="1" style="border-collapse:collapse; border-color:#ccc;">
                  <tr><th align="left">Symbol</th><td>%s</td></tr>
                  <tr><th align="left">Quantity</th><td>%d</td></tr>
                  <tr><th align="left">Avg buy price</th><td>Rs %.2f</td></tr>
                  <tr><th align="left">Current LTP</th><td>Rs %.2f</td></tr>
                  <tr><th align="left">Target limit (LTP - discount)</th><td>Rs %.2f</td></tr>
                  <tr><th align="left">Unrealised loss</th><td>-%.2f%%</td></tr>
                  <tr><th align="left">Additional cost at target</th><td>Rs %.2f</td></tr>
                </table>
                <p style="margin-top:14px; color:#555;">
                  Request: <code>%s</code><br/>
                  Time: %s
                </p>
                </body></html>
                """.formatted(
                        symbol, strategyName, orderId == null ? "(pending / failed)" : orderId,
                        symbol, quantity, avgPrice, ltp, targetPrice, lossPct, addedCost,
                        requestId == null ? "(none)" : requestId,
                        IST_TS.format(ZonedDateTime.now())
                );

        sendHtml(subject, html);
    }

    public void sendStartupAlert(int holdingsCount, int strategyCount, String requestId) {
        String subject = String.format(
                "[ALIVE] Zerodha service started — %d holdings, %d strategies online",
                holdingsCount, strategyCount);
        String html = """
                <html><body style="font-family: -apple-system, Helvetica, Arial, sans-serif;">
                <h2 style="color:#1f8b4c; margin:0 0 12px 0;">Trading service is live</h2>
                <p style="margin:0 0 8px 0;">First cron tick after JVM boot has passed the market-open gate.</p>
                <table cellpadding="6" cellspacing="0" border="1" style="border-collapse:collapse; border-color:#ccc;">
                  <tr><th align="left">Holdings cached</th><td>%d</td></tr>
                  <tr><th align="left">Strategies registered</th><td>%d</td></tr>
                  <tr><th align="left">First tick at</th><td>%s</td></tr>
                  <tr><th align="left">Request ID</th><td><code>%s</code></td></tr>
                </table>
                <p style="margin-top:14px; color:#555;">
                  Subsequent ticks will fire every 5 minutes until 15:30 IST.
                </p>
                </body></html>
                """.formatted(
                        holdingsCount,
                        strategyCount,
                        IST_TS.format(ZonedDateTime.now()),
                        requestId == null ? "(none)" : requestId
                );
        sendHtml(subject, html);
    }

    public void sendScrapeFailureAlert(String symbol, String error, String requestId) {
        String subject = String.format("[ALERT] LTP scrape failed for %s", symbol);
        String html = """
                <html><body style="font-family: -apple-system, Helvetica, Arial, sans-serif;">
                <h3 style="color:#c0392b;">Scraper failure</h3>
                <p><b>Symbol:</b> %s</p>
                <p><b>Error:</b> %s</p>
                <p style="color:#555;">Request: <code>%s</code><br/>Time: %s</p>
                </body></html>
                """.formatted(
                        symbol,
                        error == null ? "(no message)" : error,
                        requestId == null ? "(none)" : requestId,
                        IST_TS.format(ZonedDateTime.now())
                );
        sendHtml(subject, html);
    }

    public void sendOrderFailureAlert(String symbol, int quantity, double price,
                                      String error, String requestId) {
        sendOrderFailureAlert(symbol, null, quantity, price, error, requestId);
    }

    public void sendOrderFailureAlert(String symbol, String transactionType, int quantity, double price,
                                      String error, String requestId) {
        String txn = transactionType == null || transactionType.isBlank() ? "ORDER" : transactionType.toUpperCase();
        String subject = String.format("[ALERT] %s order FAILED for %s qty=%d", txn, symbol, quantity);
        String html = """
                <html><body style="font-family: -apple-system, Helvetica, Arial, sans-serif;">
                <h3 style="color:#c0392b;">Order placement failed</h3>
                <p><b>Symbol:</b> %s</p>
                <p><b>Quantity:</b> %d</p>
                <p><b>Limit price:</b> Rs %.2f</p>
                <p><b>Error:</b> %s</p>
                <p style="color:#555;">Request: <code>%s</code><br/>Time: %s</p>
                </body></html>
                """.formatted(
                        symbol, quantity, price,
                        error == null ? "(no message)" : error,
                        requestId == null ? "(none)" : requestId,
                        IST_TS.format(ZonedDateTime.now())
                );
        sendHtml(subject, html);
    }

    private void sendHtml(String subject, String html) {
        if (from == null || from.isBlank()) {
            log.warn("Mail not configured (app.mail.from / spring.mail.username missing). Skipping send. Subject={}", subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(alertTo);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("Alert mail sent to {} | subject={}", alertTo, subject);
        } catch (MessagingException | RuntimeException ex) {
            log.error("Failed to send alert mail (subject={}): {}", subject, ex.getMessage(), ex);
        }
    }
}
