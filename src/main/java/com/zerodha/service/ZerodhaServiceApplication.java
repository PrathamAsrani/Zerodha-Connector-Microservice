package com.zerodha.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ZerodhaServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ZerodhaServiceApplication.class, args);
	}
}
