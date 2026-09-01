package com.waypoint.backend.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "ai.family-access")
public record FamilyAiAccessProperties(
        long monthlyBudgetRupees,
        int maxInputTokens,
        BigDecimal usdInrAccountingRate,
        BigDecimal inputUsdPerMillionTokens,
        BigDecimal outputUsdPerMillionTokens,
        long requestReservationMicrorupees
) {
    public FamilyAiAccessProperties {
        if (monthlyBudgetRupees <= 0) monthlyBudgetRupees = 5_000;
        if (maxInputTokens <= 0) maxInputTokens = 5_000;
        if (usdInrAccountingRate == null || usdInrAccountingRate.signum() <= 0) usdInrAccountingRate = new BigDecimal("100");
        if (inputUsdPerMillionTokens == null || inputUsdPerMillionTokens.signum() <= 0) inputUsdPerMillionTokens = new BigDecimal("0.05");
        if (outputUsdPerMillionTokens == null || outputUsdPerMillionTokens.signum() <= 0) outputUsdPerMillionTokens = new BigDecimal("0.40");
        if (requestReservationMicrorupees <= 0) requestReservationMicrorupees = 2_000_000L;
    }
}
