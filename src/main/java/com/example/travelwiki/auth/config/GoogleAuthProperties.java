package com.example.travelwiki.auth.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.google")
public record GoogleAuthProperties(
    @NotEmpty(message = "At least one Google allowed audience must be configured")
    List<String> allowedAudiences
) {
}
