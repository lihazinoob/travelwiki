package com.example.travelwiki.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
    @NotBlank(message = "JWT secret must be configured (auth.jwt.secret / JWT_SECRET)")
    String secret,

    @Min(value = 1, message = "Access token TTL must be at least 1 minute")
    int accessTokenTtlMinutes,

    @Min(value = 1, message = "Refresh token TTL must be at least 1 day")
    int refreshTokenTtlDays
) {
}
