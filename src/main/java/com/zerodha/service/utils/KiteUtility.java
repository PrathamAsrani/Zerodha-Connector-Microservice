package com.zerodha.service.utils;

import com.zerodhatech.kiteconnect.KiteConnect;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import org.springframework.stereotype.Component;

@Component
public class KiteUtility {
    private KiteConnect kiteSdk;

    @Value("${ZERODHA_API_KEY}")
    private String apiKey;

    private static final Logger logger = LoggerFactory.getLogger(KiteUtility.class);

    @PostConstruct   //executed once after the bean's dependencies have been injected, but before the bean is put into service
    public void init(){
        logger.info("Initializing KiteConnect with apiKey");
        kiteSdk = new KiteConnect(apiKey);
        logger.info("KiteConnect initialized");
    }
    public KiteConnect getKiteSdk() {
        logger.info("getKiteSdk called");
        return kiteSdk;
    }
}
