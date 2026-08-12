package com.waypoint.backend.config.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "lemon-squeezy")
public record LemonSqueezyProperties(
        @NotBlank String apiKey,
        @NotBlank String storeId,
        @NotBlank String monthlyVariantId,
        @NotBlank String annualVariantId,
        @NotBlank String webhookSecret,
        @NotBlank @Pattern(regexp = "https://.+") String apiBaseUrl
) {
}
