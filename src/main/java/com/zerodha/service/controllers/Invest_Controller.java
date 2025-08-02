package com.zerodha.service.controllers;

import com.zerodha.service.model.dtos.OrderRequestDto;
import com.zerodha.service.model.dtos.OrderResponseDto;
import com.zerodha.service.services.ZerodhaInvestService;
import com.zerodha.service.services.ZerodhaUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/invest")
public class Invest_Controller {
    private ZerodhaInvestService zerodhaInvestService;

    public Invest_Controller(ZerodhaInvestService zerodhaInvestService){
        this.zerodhaInvestService=zerodhaInvestService;
    }

    @PostMapping("/place-order")
    @Async
    public CompletableFuture<ResponseEntity<Object>> placeOrder(@RequestBody OrderRequestDto orderRequestDto){
        return CompletableFuture.completedFuture(zerodhaInvestService.placeOrder(orderRequestDto));
    }
}
