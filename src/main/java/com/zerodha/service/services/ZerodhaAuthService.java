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

    public ResponseEntity<String> login(){
        try{
            logger.info("clientId: {}, clientSecret: {}", clientId, clientSecret);
            KiteConnect kiteSdk = this.kiteUtility.getKiteSdk();
            kiteSdk.setUserId(clientId);
            logger.info("userId is set in kiteSdk");

            String loginUrl = kiteSdk.getLoginURL();

            logger.info("Login URL retrieved: {}", loginUrl);

            return ResponseEntity.ok("Login successful for user: " + loginUrl);
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

            logger.info("Setting request token and public token which are obtained from login process.");
            kiteSdk.setAccessToken(user.accessToken);
            kiteSdk.setPublicToken(user.publicToken);

            logger.info("Setting session expiry callback.");
            kiteSdk.setSessionExpiryHook(new SessionExpiryHook(){
                @Override
                public void sessionExpired(){
                    System.out.println("Session expired.");
                }
            });

            return ResponseEntity.ok(user);
        }catch (KiteException ex) {
            logger.error("KiteException during session generation: {}", ex.getMessage());
            return ResponseEntity.internalServerError().body("Zerodha session generation failed: " + ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in fallback: {}", ex.getMessage());
            return ResponseEntity.internalServerError().body("Unexpected error: " + ex.getMessage());
        }
    }
}
