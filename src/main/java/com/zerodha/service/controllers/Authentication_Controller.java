package com.zerodha.service.controllers;

import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/auth")
public class Authentication_Controller {
    @Value("${ZERODHA_CLIENT_ID}")
    private String clientId;
    @Value("${ZERODHA_API_SECRET}")
    private String clientSecret;

    private static final Logger logger = LoggerFactory.getLogger(Authentication_Controller.class);
    private final KiteUtility kiteUtility;
    private String loginUrl;


    @Autowired
    public Authentication_Controller(KiteUtility kiteUtility){
        this.kiteUtility = kiteUtility;
        this.logger.info("Authentication initialized.");
    }

    @GetMapping("/login")
    @Async
    CompletableFuture<ResponseEntity<String>> login(){
        try{
            KiteConnect kiteSdk = this.kiteUtility.getKiteSdk();
            kiteSdk.setUserId(clientId);
            logger.info("userId is set in kiteSdk");

            this.loginUrl = kiteSdk.getLoginURL();
            logger.info("Login URL retrieved: {}", loginUrl);

            return CompletableFuture.completedFuture(ResponseEntity.ok("Login successful for user: " + this.loginUrl));
        }
        catch (Exception ex) {
            logger.error("Unexpected error: {}", ex.getMessage());
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError()
                    .body("Unexpected error: " + ex.getMessage()));
        }
    }

    @GetMapping("/login-fallback")
    @Async
    CompletableFuture<ResponseEntity<Object>> loginFallback(@RequestParam("request_token") String reqToken){
        try{
            KiteConnect kiteSdk = this.kiteUtility.getKiteSdk();
            kiteSdk.setUserId(clientId);
            logger.info("Received request_token: {}", reqToken);

            User user = kiteSdk.generateSession(reqToken, clientSecret);
            logger.info("User session generated successfully for userId: {}", user.userId);

            return CompletableFuture.completedFuture(ResponseEntity.ok(user));
        }catch (KiteException ex) {
            logger.error("KiteException during session generation: {}", ex.getMessage());
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError()
                    .body("Zerodha session generation failed: " + ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error in fallback: {}", ex.getMessage());
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError()
                    .body("Unexpected error: " + ex.getMessage()));
        }
    }
}
