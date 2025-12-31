package com.zerodha.service.services;

import com.zerodha.service.controllers.Authentication_Controller;
import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.SessionExpiryHook;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ZerodhaAuthService {
    @Value("${ZERODHA_CLIENT_ID}")
    private String clientId;

    @Value("${ZERODHA_API_SECRET}")
    private String clientSecret;

    private static final Logger logger = LoggerFactory.getLogger(Authentication_Controller.class);
    private final KiteUtility kiteUtility;

    @Autowired
    public ZerodhaAuthService(KiteUtility kiteUtility){
        this.kiteUtility = kiteUtility;
    }

    /**
     * Get authenticated KiteConnect instance
     */
    public KiteConnect getAuthenticatedKiteSdk() {
        if (!kiteUtility.hasTokens()) {
            logger.error("No authentication tokens available. Please login first.");
            throw new RuntimeException("Authentication required. Please login first at /auth/login");
        }

        return kiteUtility.getKiteSdk();
    }

    public ResponseEntity<String> login(){
        try{
            logger.info("clientId: {}, clientSecret: {}", clientId, clientSecret);

            // Check if already authenticated
            if (kiteUtility.hasTokens()) {
                logger.info("Already authenticated for userId: {}", kiteUtility.getCurrentUserId());
                return ResponseEntity.ok("Already authenticated. Use /auth/logout to logout first.");
            }

            KiteConnect kiteSdk = this.kiteUtility.getKiteSdk();
            kiteSdk.setUserId(clientId);
            logger.info("userId is set in kiteSdk");

            String loginUrl = kiteSdk.getLoginURL();
            logger.info("Login URL retrieved: {}", loginUrl);

            return ResponseEntity.ok("Login URL: " + loginUrl);
        }
        catch (Exception ex) {
            logger.error("Unexpected error: {}", ex.getMessage());
            return ResponseEntity.internalServerError().body("Unexpected error: " + ex.getMessage());
        }
    }

    public ResponseEntity<Object> handleLoginCallback(String reqToken){
        try{
            KiteConnect kiteSdk = this.kiteUtility.getKiteSdk();
            kiteSdk.setUserId(clientId);
            logger.info("Received request_token: {}", reqToken);

            User user = kiteSdk.generateSession(reqToken, clientSecret);
            logger.info("User session generated successfully for userId: {}", user.userId);

            logger.info("Setting and storing access token and public token.");

            // ⭐ CRITICAL: Store tokens in KiteUtility so all subsequent calls use them
            kiteUtility.setTokens(user.userId, user.accessToken, user.publicToken);

            logger.info("Setting session expiry callback.");
            kiteSdk.setSessionExpiryHook(new SessionExpiryHook(){
                @Override
                public void sessionExpired(){
                    logger.warn("Session expired for userId: {}", user.userId);
                    kiteUtility.clearTokens();
                }
            });

            logger.info("Authentication successful. Tokens stored for future API calls.");
            return ResponseEntity.ok(user);

        } catch (KiteException ex) {
            logger.error("KiteException during session generation: {}", ex.getMessage());
            return ResponseEntity.internalServerError().body("Zerodha session generation failed: " + ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in fallback: {}", ex.getMessage());
            return ResponseEntity.internalServerError().body("Unexpected error: " + ex.getMessage());
        }
    }

    /**
     * Logout and clear tokens
     */
    public ResponseEntity<String> logout() {
        try {
            logger.info("Logout requested for userId: {}", kiteUtility.getCurrentUserId());
            kiteUtility.clearTokens();
            logger.info("Logout successful. Tokens cleared.");
            return ResponseEntity.ok("Logged out successfully");
        } catch (Exception ex) {
            logger.error("Error during logout: {}", ex.getMessage());
            return ResponseEntity.internalServerError().body("Logout failed: " + ex.getMessage());
        }
    }

    /**
     * Check authentication status
     */
    public ResponseEntity<String> checkAuthStatus() {
        if (kiteUtility.hasTokens()) {
            return ResponseEntity.ok("Authenticated as userId: " + kiteUtility.getCurrentUserId());
        } else {
            return ResponseEntity.ok("Not authenticated. Please login at /auth/login");
        }
    }
}
