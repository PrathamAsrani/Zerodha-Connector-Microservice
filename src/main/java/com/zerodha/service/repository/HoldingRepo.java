package com.zerodha.service.repository;

import com.zerodha.service.model.HoldingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepo extends JpaRepository<HoldingEntity, Long> {
}
