package com.waypoint.backend.model.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminFamilyAiUserUsageResponse(
        UUID grantId,
        UUID userId,
        String email,
        String displayName,
        String provider,
        Instant userCreatedAt,
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
