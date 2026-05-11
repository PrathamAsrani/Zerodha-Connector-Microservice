package com.zerodha.service.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerodha.service.dtos.auth.StoredTokenDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

@Service
public class TokenStorageService {
    private static final Logger logger = LoggerFactory.getLogger(TokenStorageService.class);

    private final Path filePath;
    private final ObjectMapper objectMapper;

    public TokenStorageService(@Value("${kite.token.file:./data/kite-token.json}") String configuredPath) {
        this.filePath = Paths.get(configuredPath).toAbsolutePath();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        logger.info("Token storage path: {}", filePath);
    }

    public Optional<StoredTokenDto> load() {
        if (!Files.exists(filePath)) {
            logger.info("No token file at {} — fresh login required.", filePath);
            return Optional.empty();
        }
        try {
            StoredTokenDto stored = objectMapper.readValue(filePath.toFile(), StoredTokenDto.class);
            if (stored == null
                    || stored.getAccessToken() == null || stored.getAccessToken().isBlank()
                    || stored.getUserId() == null || stored.getUserId().isBlank()) {
                logger.info("Token file at {} is empty/null — fresh login required.", filePath);
                return Optional.empty();
            }
            logger.info("Loaded token from {} (userId={}, savedAt={}).",
                    filePath, stored.getUserId(), stored.getSavedAt());
            return Optional.of(stored);
        } catch (IOException ex) {
            logger.error("Failed to read token file {}: {}", filePath, ex.getMessage());
            return Optional.empty();
        }
    }

    public void save(String userId, String accessToken, String publicToken) {
        StoredTokenDto dto = StoredTokenDto.builder()
                .userId(userId)
                .accessToken(accessToken)
                .publicToken(publicToken)
                .savedAt(Instant.now())
                .build();
        try {
            Files.createDirectories(filePath.getParent());
            objectMapper.writeValue(filePath.toFile(), dto);
            logger.info("Persisted token to {} (userId={}).", filePath, userId);
        } catch (IOException ex) {
            logger.error("Failed to persist token to {}: {}", filePath, ex.getMessage());
        }
    }

    public void clear() {
        try {
            Files.createDirectories(filePath.getParent());
            // Keep the file but null out values (matches the user-requested {token: null} shape).
            objectMapper.writeValue(filePath.toFile(), new StoredTokenDto(null, null, null, Instant.now()));
            logger.info("Cleared token file at {}.", filePath);
        } catch (IOException ex) {
            logger.error("Failed to clear token file {}: {}", filePath, ex.getMessage());
        }
    }
}
