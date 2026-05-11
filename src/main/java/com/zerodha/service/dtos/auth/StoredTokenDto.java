package com.zerodha.service.dtos.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredTokenDto {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("public_token")
    private String publicToken;

    @JsonProperty("saved_at")
    private Instant savedAt;
}
