package com.waypoint.backend.model.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.waypoint.backend.model.plan.PlanCode;

import java.time.Instant;
import java.util.UUID;

public record AdminFamilyAiUserUsageResponse(
        UUID grantId,
        UUID userId,
        String email,
        String displayName,
        String pictureUrl,
        String phoneNumber,
        String phoneCountryCode,
        String provider,
        String providerUserId,
        PlanCode persistedPlan,
        Instant userCreatedAt,
        Instant userUpdatedAt,
        Instant lastLoginAt,
        boolean active,
        Instant validUntil,
        String reason,
        String grantedBy,
        Instant grantedAt,
        String revokedBy,
        Instant revokedAt,
        @JsonIgnore long monthlyAllowanceMicrorupees,
        @JsonIgnore long spentMicrorupees,
        @JsonIgnore long remainingMicrorupees,
        double usagePercent,
        String status
) {
    @JsonProperty("monthlyAllowanceRupees")
    public double monthlyAllowanceRupees() {
        return toRupees(monthlyAllowanceMicrorupees);
    }

    @JsonProperty("spentRupees")
    public double spentRupees() {
        return toRupees(spentMicrorupees);
    }

    @JsonProperty("remainingRupees")
    public double remainingRupees() {
        return toRupees(remainingMicrorupees);
    }

    private static double toRupees(long microrupees) {
        return microrupees / 1_000_000.0;
    }
}
