package com.waypoint.backend.model.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditEventResponse(
        UUID id,
        String adminId,
        String action,
        String resourceType,
        String resourceId,
        String details,
        Instant createdAt
) {
}
