package com.waypoint.backend.model.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record AdminFamilyAiUsageResponse(
        @JsonIgnore long monthlyPoolMicrorupees,
        @JsonIgnore long poolSpentMicrorupees,
        @JsonIgnore long poolRemainingMicrorupees,
        double poolUsagePercent,
        int activeSpecialUsers,
        int requestTokenLimit,
        String periodKey,
        Instant resetsAt,
        List<AdminFamilyAiUserUsageResponse> users
) {
    @JsonProperty("monthlyPoolRupees")
    public double monthlyPoolRupees() {
        return toRupees(monthlyPoolMicrorupees);
    }

    @JsonProperty("poolSpentRupees")
    public double poolSpentRupees() {
        return toRupees(poolSpentMicrorupees);
    }

    @JsonProperty("poolRemainingRupees")
    public double poolRemainingRupees() {
        return toRupees(poolRemainingMicrorupees);
    }

    private static double toRupees(long microrupees) {
        return microrupees / 1_000_000.0;
    }
}
