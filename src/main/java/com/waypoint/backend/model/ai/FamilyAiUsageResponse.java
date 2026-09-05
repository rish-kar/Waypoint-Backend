package com.waypoint.backend.model.ai;

import java.time.Instant;

public record FamilyAiUsageResponse(
        boolean specialAccess,
        int requestTokenLimit,
        long monthlyAllowanceMicrorupees,
        long spentMicrorupees,
        long remainingMicrorupees,
        double usagePercent,
        String periodKey,
        Instant resetsAt,
        String status
) {
}
