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
        long monthlyRequestCount,
        long monthlyInputTokens,
        @JsonIgnore long sessionLimitMicrorupees,
        @JsonIgnore long sessionSpentMicrorupees,
        @JsonIgnore long sessionRemainingMicrorupees,
        double sessionUsagePercent,
        Instant sessionResetsAt,
        long sessionRequestCount,
        long sessionInputTokens,
        @JsonIgnore long weeklyLimitMicrorupees,
        @JsonIgnore long weeklySpentMicrorupees,
        @JsonIgnore long weeklyRemainingMicrorupees,
        double weeklyUsagePercent,
        Instant weeklyResetsAt,
        long weeklyRequestCount,
        long weeklyInputTokens,
        String status
) {
    @JsonProperty("monthlyAllowanceRupees")
    public double monthlyAllowanceRupees() { return toRupees(monthlyAllowanceMicrorupees); }

    @JsonProperty("spentRupees")
    public double spentRupees() { return toRupees(spentMicrorupees); }

    @JsonProperty("remainingRupees")
    public double remainingRupees() { return toRupees(remainingMicrorupees); }

    @JsonProperty("sessionLimitRupees")
    public double sessionLimitRupees() { return toRupees(sessionLimitMicrorupees); }

    @JsonProperty("sessionSpentRupees")
    public double sessionSpentRupees() { return toRupees(sessionSpentMicrorupees); }

    @JsonProperty("sessionRemainingRupees")
    public double sessionRemainingRupees() { return toRupees(sessionRemainingMicrorupees); }

    @JsonProperty("weeklyLimitRupees")
    public double weeklyLimitRupees() { return toRupees(weeklyLimitMicrorupees); }

    @JsonProperty("weeklySpentRupees")
    public double weeklySpentRupees() { return toRupees(weeklySpentMicrorupees); }

    @JsonProperty("weeklyRemainingRupees")
    public double weeklyRemainingRupees() { return toRupees(weeklyRemainingMicrorupees); }

    private static double toRupees(long microrupees) { return microrupees / 1_000_000.0; }
}
