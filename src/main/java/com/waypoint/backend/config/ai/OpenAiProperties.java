package com.waypoint.backend.config.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "ai.openai")
public record OpenAiProperties(
        boolean enabled,
        @NotBlank @Pattern(regexp = "https?://.+") String baseUrl,
        String apiKey,
        @NotBlank String model,
        @NotNull Duration requestTimeout
) {
}
