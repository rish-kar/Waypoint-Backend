package com.waypoint.backend.entitlement;

import java.time.Instant;
import java.util.List;

public record EntitlementResponse(
        String plan,
        String status,
        boolean premium,
        List<String> features,
        Instant validUntil,
        Instant checkedAt
) {
}
