package com.zerodha.service.services;

import com.zerodha.service.enums.LogEnum;
import com.zerodha.service.dtos.logging.LogPayLoadDTO;
import com.zerodha.service.dtos.logging.LogRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class LogService {
    private final WebClient webClient;

    private final Logger logger = LoggerFactory.getLogger(LogService.class);

    public LogService(WebClient.Builder builder , @Value("${LOGGER_BASE_URI:https://microservice-logger-springboot.onrender.com/}")String baseUri){
        this.webClient = builder.baseUrl(baseUri).build();
    }

    public void sendLog(String message , LogEnum level){

        LogPayLoadDTO logPayLoadDTO = new LogPayLoadDTO(message , "admin");

        LogRequestDTO logRequestDTO = new LogRequestDTO(logPayLoadDTO , level);

        webClient.post()
                .uri("api/logs/create-log")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(logRequestDTO)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> logger.info("Log sent successfully "+response))
                .doOnError(error -> logger.error("Failed to send log: "+ error.getMessage()))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }
}
