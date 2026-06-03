package com.zerodha.service.repository;

import com.zerodha.service.enums.TransactionEnum;
import com.zerodha.service.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface OrderRepo extends JpaRepository<OrderEntity ,String> {

    boolean existsByTradingsymbolAndTransactionTypeAndPlacedAtBetween(
            String tradingsymbol, TransactionEnum transactionType, Instant start, Instant end);
}
