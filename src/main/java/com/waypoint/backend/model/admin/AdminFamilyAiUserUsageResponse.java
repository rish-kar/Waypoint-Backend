package com.waypoint.backend.model.admin;

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
        long monthlyAllowanceMicrorupees,
        long spentMicrorupees,
        long remainingMicrorupees,
        double usagePercent,
        String status
) {
}
