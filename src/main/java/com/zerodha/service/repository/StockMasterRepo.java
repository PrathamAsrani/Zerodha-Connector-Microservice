package com.zerodha.service.repository;

import com.zerodha.service.model.StockMasterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMasterRepo extends JpaRepository<StockMasterEntity, Long> {
    List<StockMasterEntity> findByActiveTrue();
}
