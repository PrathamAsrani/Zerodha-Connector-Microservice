package com.zerodha.service.services;

import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Margin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ZerodhaUserService {
    private final KiteUtility kiteUtility;
    private final ZerodhaAuthService authService;
    private static final Logger logger = LoggerFactory.getLogger(ZerodhaUserService.class);
    @Autowired
    public ZerodhaUserService(KiteUtility kiteUtility, ZerodhaAuthService authService){
        this.kiteUtility = kiteUtility;
        this.authService = authService;
    }

    public ResponseEntity<Object> getUserMargins(String indicator){
        logger.info("started getUserMargins.");
        KiteConnect kiteSdk = kiteUtility.getKiteSdk();

        logger.info("KiteSdk got.");
        if(kiteSdk.getAccessToken() == null){
            logger.error("accesstoken is null.");
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Please complete the authentication first");
            error.put("loginUrl", this.authService.login());

            return ResponseEntity.status(401).body(error);
        }

        logger.info("getting margins.");

        try{
            Margin margins = kiteSdk.getMargins(indicator);

            logger.info("creating res object.");
            Map<String, Object> res = new HashMap<>();
            res.put("available_cash", margins.available.cash);
            res.put("utilized_debits", margins.utilised.debits);
            res.put("margin", margins);

            logger.info("returing res entity.");
            return ResponseEntity.status(200).body(res);
        }
        catch (KiteException e) {
            logger.info("Kite exception caugth");
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to fetch margin details: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
        catch (Exception e) {
            logger.info("Exception caugth");
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to fetch margin details: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
