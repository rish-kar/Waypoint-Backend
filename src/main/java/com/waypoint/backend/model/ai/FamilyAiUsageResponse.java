package com.waypoint.backend.model.ai;

import java.time.Instant;

public record FamilyAiUsageResponse(
        boolean specialAccess,
        int activeSpecialUsers,
        long monthlyPoolMicrorupees,
        long monthlyAllowanceMicrorupees,
        long spentMicrorupees,
        long remainingMicrorupees,
        double usagePercent,
        int requestTokenLimit,
        String periodKey,
        Instant resetsAt,
        String status
) {
}
