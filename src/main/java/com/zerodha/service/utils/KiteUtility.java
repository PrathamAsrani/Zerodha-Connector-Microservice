package com.zerodha.service.utils;

import com.zerodhatech.kiteconnect.KiteConnect;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
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

    private static final Logger logger = LoggerFactory.getLogger(KiteUtility.class);

    @PostConstruct
    public void init(){
        logger.info("Initializing KiteConnect with apiKey");
        kiteSdk = new KiteConnect(apiKey);
        logger.info("KiteConnect initialized");
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
