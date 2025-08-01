package com.zerodha.service.controllers;

import com.zerodha.service.services.ZerodhaUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/user")
public class User_Controller {
    private final ZerodhaUserService userService;

    @Autowired
    public User_Controller(ZerodhaUserService userService){
        this.userService = userService;
    }

    @GetMapping("/get-user-margins")
    @Async
    public CompletableFuture<ResponseEntity<Object>> GetUserDetails(@RequestParam("type") String indicator){
        String[] allowedTypes = {"equity", "commodity"};
        var res = Arrays.stream(allowedTypes).anyMatch(s -> s.equals(indicator.toLowerCase().trim()));
        if(!res){
            CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            ResponseEntity
                                    .status(400)
                                    .body(Map.of("message", "Type can be equity or commodity."))
                                    .toString()
                    )
            );
        }
        return CompletableFuture.completedFuture(this.userService.getUserMargins(indicator));
    }
}
