package com.zerodha.service.services;

import com.zerodha.service.enums.PropertyStatusEnum;
import com.zerodha.service.model.PropertyEntity;
import com.zerodha.service.repository.PropertyRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Thin read accessor over the {@code properties} table. Every lookup falls back
 * to a caller-supplied default so a missing/garbled row can never break the
 * trading flow — the DB is an override, not a hard dependency.
 */
@Service
public class PropertyService {

    private static final Logger logger = LoggerFactory.getLogger(PropertyService.class);

    private final PropertyRepo propertyRepo;

    public PropertyService(PropertyRepo propertyRepo) {
        this.propertyRepo = propertyRepo;
    }

    /** Raw ACTIVE value for {@code name}, or {@code defaultValue} if absent. */
    public String getString(String name, String defaultValue) {
        try {
            Optional<PropertyEntity> row =
                    propertyRepo.findFirstByNameAndStatus(name, PropertyStatusEnum.ACTIVE);
            if (row.isPresent() && row.get().getValue() != null && !row.get().getValue().isBlank()) {
                return row.get().getValue().trim();
            }
        } catch (Exception ex) {
            logger.warn("Failed to read property '{}'; using default '{}'. ({})",
                    name, defaultValue, ex.getMessage());
        }
        return defaultValue;
    }

    /** ACTIVE value parsed as a double, or {@code defaultValue} on miss/parse error. */
    public double getDouble(String name, double defaultValue) {
        String raw = getString(name, null);
        if (raw == null) return defaultValue;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            logger.warn("Property '{}' value '{}' is not a valid double; using default {}.",
                    name, raw, defaultValue);
            return defaultValue;
        }
    }
}
