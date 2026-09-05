package com.waypoint.backend.model.ai;

import java.time.Instant;

public record FamilyAiUsageResponse(
        boolean specialAccess,
        int requestTokenLimit,
        int sessionWindowHours,
        double sessionUsagePercent,
        Instant sessionResetsAt,
        int weeklyWindowDays,
        double weeklyUsagePercent,
        Instant weeklyResetsAt,
        String status
) {
}
