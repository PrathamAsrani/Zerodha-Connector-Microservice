package com.zerodha.service.services;

import com.zerodha.service.dtos.scraper.ScreenerLtpDto;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScreenerScraperService {
    private static final Logger logger = LoggerFactory.getLogger(ScreenerScraperService.class);

    private static final String BASE_URL = "https://www.screener.in/company/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int TIMEOUT_MS = 10_000;
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    public ScreenerLtpDto fetchLtp(String symbol) throws IOException {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        String key = symbol.trim().toUpperCase();

        // Try /company/{SYMBOL}/ first; if that 404s, fall back to consolidated
        // (some companies are only present at the /consolidated/ slug).
        CachedEntry hit = cache.get(key);
        if (hit != null && Duration.between(hit.dto.getFetchedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            logger.info("Cache hit for symbol={} (age {}s)", key,
                    Duration.between(hit.dto.getFetchedAt(), Instant.now()).toSeconds());
            ScreenerLtpDto cached = copyOf(hit.dto);
            cached.setFromCache(true);
            return cached;
        }

        ScreenerLtpDto dto;
        try {
            dto = scrape(key, BASE_URL + key + "/");
        } catch (HttpStatusException ex) {
            if (ex.getStatusCode() == 404) {
                logger.info("404 on /company/{}/, retrying /consolidated/", key);
                dto = scrape(key, BASE_URL + key + "/consolidated/");
            } else {
                throw ex;
            }
        }

        cache.put(key, new CachedEntry(dto));
        return dto;
    }

    private ScreenerLtpDto scrape(String symbol, String url) throws IOException {
        logger.info("Scraping screener.in: {}", url);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        Double price = extractPrice(doc);
        if (price == null) {
            throw new IllegalStateException("Could not find Current Price on screener.in page for " + symbol);
        }

        String companyName = extractCompanyName(doc, symbol);

        return ScreenerLtpDto.builder()
                .symbol(symbol)
                .companyName(companyName)
                .lastPrice(price)
                .source("screener.in")
                .sourceUrl(url)
                .fetchedAt(Instant.now())
                .fromCache(false)
                .build();
    }

    private Double extractPrice(Document doc) {
        Elements items = doc.select("#top-ratios li");
        for (Element item : items) {
            Element name = item.selectFirst(".name");
            if (name == null) continue;
            if (!name.text().trim().equalsIgnoreCase("Current Price")) continue;

            Element value = item.selectFirst(".value");
            if (value == null) continue;

            String raw = value.text();                  // e.g. "₹ 1,234.56" or "1,234.56"
            String cleaned = raw.replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) continue;

            try {
                return Double.parseDouble(cleaned);
            } catch (NumberFormatException ex) {
                logger.warn("Could not parse price text '{}'", raw);
            }
        }
        return null;
    }

    private String extractCompanyName(Document doc, String fallback) {
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().trim();
        }
        return fallback;
    }

    private static ScreenerLtpDto copyOf(ScreenerLtpDto src) {
        return ScreenerLtpDto.builder()
                .symbol(src.getSymbol())
                .companyName(src.getCompanyName())
                .lastPrice(src.getLastPrice())
                .source(src.getSource())
                .sourceUrl(src.getSourceUrl())
                .fetchedAt(src.getFetchedAt())
                .fromCache(src.getFromCache())
                .build();
    }

    private record CachedEntry(ScreenerLtpDto dto) {}
}
