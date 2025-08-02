package com.zerodha.service.repository;

import com.zerodha.service.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepo extends JpaRepository<OrderEntity ,String> {
}
