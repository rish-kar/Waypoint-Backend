package com.waypoint.backend.model.admin;

import java.time.Instant;
import java.util.List;

public record AdminFamilyAiUsageResponse(
        long monthlyPoolMicrorupees,
        long poolSpentMicrorupees,
        long poolRemainingMicrorupees,
        double poolUsagePercent,
        int activeSpecialUsers,
        int requestTokenLimit,
        String periodKey,
        Instant resetsAt,
        List<AdminFamilyAiUserUsageResponse> users
) {
}
