package com.zerodha.service.controllers;

import com.zerodha.service.model.dtos.OrderRequestDto;
import com.zerodha.service.model.dtos.OrderResponseDto;
import com.zerodha.service.services.OrderService;
import com.zerodha.service.services.ZerodhaInvestService;
import com.zerodha.service.services.ZerodhaUserService;
import com.zerodhatech.models.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/invest")
public class Invest_Controller {
    private ZerodhaInvestService zerodhaInvestService;
    private OrderService orderService;

    public Invest_Controller(ZerodhaInvestService zerodhaInvestService, OrderService orderService){
        this.zerodhaInvestService=zerodhaInvestService;
        this.orderService = orderService;
    }

    @PostMapping(
            value = "/place-order",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Async
    public CompletableFuture<ResponseEntity<OrderResponseDto>> placeOrder(@RequestBody OrderRequestDto orderRequestDto){
        return CompletableFuture.completedFuture(zerodhaInvestService.placeOrder(orderRequestDto));
    }

    @GetMapping("/get-orders")
    @Async
    public CompletableFuture<ResponseEntity<Object>> getOrders(){
        return CompletableFuture.completedFuture(orderService.getOrders());
    }
}
