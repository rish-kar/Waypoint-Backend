package com.waypoint.backend.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lemon-squeezy")
public record LemonSqueezyProperties(
        String apiKey,
        String storeId,
        String monthlyVariantId,
        String annualVariantId,
        String webhookSecret,
        String apiBaseUrl
) {
}
