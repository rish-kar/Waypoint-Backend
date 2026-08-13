package com.waypoint.backend.model.admin;

import java.time.Instant;
import java.util.UUID;

public record PremiumSpecialGrantResponse(
        UUID id,
        UUID userId,
        String email,
        String plan,
        String status,
        boolean active,
        Instant validUntil,
        String reason,
        String grantedBy,
        Instant grantedAt,
        String revokedBy,
        Instant revokedAt
) {
}
