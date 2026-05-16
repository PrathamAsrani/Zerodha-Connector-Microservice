package com.zerodha.service.repository;

import com.zerodha.service.model.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepo extends JpaRepository<TradeEntity, Long> {
}
