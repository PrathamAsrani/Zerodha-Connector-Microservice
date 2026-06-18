package com.zerodha.service.repository;

import com.zerodha.service.enums.PropertyStatusEnum;
import com.zerodha.service.model.PropertyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PropertyRepo extends JpaRepository<PropertyEntity, Long> {

    Optional<PropertyEntity> findFirstByNameAndStatus(String name, PropertyStatusEnum status);
}
