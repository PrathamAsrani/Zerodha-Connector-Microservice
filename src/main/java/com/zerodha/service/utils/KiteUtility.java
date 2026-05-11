package com.zerodha.service.utils;

import com.zerodha.service.dtos.auth.StoredTokenDto;
import com.zerodha.service.services.TokenStorageService;
import com.zerodhatech.kiteconnect.KiteConnect;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KiteUtility {
    private KiteConnect kiteSdk;

    // Store tokens in memory
    private String currentAccessToken;
    private String currentPublicToken;
    private String currentUserId;

    @Value("${ZERODHA_API_KEY}")
    private String apiKey;

    private final TokenStorageService tokenStorage;

    private static final Logger logger = LoggerFactory.getLogger(KiteUtility.class);

    public KiteUtility(TokenStorageService tokenStorage) {
        this.tokenStorage = tokenStorage;
    }

    @PostConstruct
    public void init(){
        logger.info("Initializing KiteConnect with apiKey");
        kiteSdk = new KiteConnect(apiKey);
        logger.info("KiteConnect initialized");

        // Restore tokens from local storage if present (in-process auto-login).
        // Note: Kite access tokens expire daily ~06:00 IST, so a stale token will
        // surface as a 403 on first API call and the user will need to re-login.
        tokenStorage.load().ifPresent(this::applyStoredToken);
    }

    private void applyStoredToken(StoredTokenDto stored) {
        logger.info("Restoring stored tokens for userId: {}", stored.getUserId());
        this.currentUserId = stored.getUserId();
        this.currentAccessToken = stored.getAccessToken();
        this.currentPublicToken = stored.getPublicToken();
        kiteSdk.setUserId(stored.getUserId());
        kiteSdk.setAccessToken(stored.getAccessToken());
        kiteSdk.setPublicToken(stored.getPublicToken());
    }

    /**
     * Get KiteConnect SDK with current access token (if available)
     */
    public KiteConnect getKiteSdk() {
        logger.info("getKiteSdk called");

        // If we have stored tokens, set them on the SDK
        if (currentAccessToken != null && currentPublicToken != null && currentUserId != null) {
            logger.info("Setting stored tokens on KiteConnect instance for userId: {}", currentUserId);
            kiteSdk.setUserId(currentUserId);
            kiteSdk.setAccessToken(currentAccessToken);
            kiteSdk.setPublicToken(currentPublicToken);
        } else {
            logger.warn("No stored tokens available. SDK may not be authenticated.");
        }

        return kiteSdk;
    }

    /**
     * Store tokens for subsequent API calls
     */
    public void setTokens(String userId, String accessToken, String publicToken) {
        logger.info("Storing tokens for userId: {}", userId);
        this.currentUserId = userId;
        this.currentAccessToken = accessToken;
        this.currentPublicToken = publicToken;

        // Set on the SDK immediately
        kiteSdk.setUserId(userId);
        kiteSdk.setAccessToken(accessToken);
        kiteSdk.setPublicToken(publicToken);

        // Persist so the next process boot can resume without re-login.
        tokenStorage.save(userId, accessToken, publicToken);

        logger.info("Tokens stored and set on KiteConnect SDK");
    }

    /**
     * Clear stored tokens (on logout or session expiry)
     */
    public void clearTokens() {
        logger.info("Clearing stored tokens for userId: {}", currentUserId);
        this.currentUserId = null;
        this.currentAccessToken = null;
        this.currentPublicToken = null;
        tokenStorage.clear();
        logger.info("Tokens cleared");
    }

    /**
     * Check if tokens are set
     */
    public boolean hasTokens() {
        return currentAccessToken != null && currentPublicToken != null && currentUserId != null;
    }

    /**
     * Get current user ID
     */
    public String getCurrentUserId() {
        return currentUserId;
    }
}
