package com.zerodha.service.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@RestController
public class Health_Controller {
    Logger logger = LoggerFactory.getLogger(Health_Controller.class);

    @GetMapping("/")
    @Async
    public CompletableFuture<ResponseEntity<String>> CheckHealth() {
        return CompletableFuture.completedFuture(ResponseEntity.ok("Zerodha Server is Running"));
    }
}
