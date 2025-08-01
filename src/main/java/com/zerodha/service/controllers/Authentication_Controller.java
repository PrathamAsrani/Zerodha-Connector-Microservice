package com.zerodha.service.controllers;

import com.zerodha.service.services.ZerodhaAuthService;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/auth")
public class Authentication_Controller {
    private final ZerodhaAuthService authService;

    public Authentication_Controller(ZerodhaAuthService authService){
        this.authService = authService;
    }

    @GetMapping("/login")
    @Async
    CompletableFuture<ResponseEntity<String>> login(){
        return CompletableFuture.completedFuture(authService.login());
    }

    @GetMapping("/login-fallback")
    @Async
    CompletableFuture<ResponseEntity<Object>> loginFallback(@RequestParam("request_token") String reqToken){
        return CompletableFuture.completedFuture(authService.handleLoginCallback(reqToken));
    }
}
