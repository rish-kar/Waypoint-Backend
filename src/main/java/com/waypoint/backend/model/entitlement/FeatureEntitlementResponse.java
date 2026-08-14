package com.waypoint.backend.model.entitlement;

import java.time.Instant;

public record FeatureEntitlementResponse(
        String feature,
        boolean allowed,
        String plan,
        String status,
        Instant validUntil,
        Instant checkedAt
) {
}
