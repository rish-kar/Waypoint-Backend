package com.waypoint.backend.model.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record FamilyAiUsageResponse(
        boolean specialAccess,
        int requestTokenLimit,
        @JsonIgnore long monthlyAllowanceMicrorupees,
        @JsonIgnore long spentMicrorupees,
        @JsonIgnore long remainingMicrorupees,
        double usagePercent,
        String periodKey,
        Instant resetsAt,
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
