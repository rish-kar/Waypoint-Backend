package com.waypoint.backend.model.subscription;

import java.time.Instant;

public record SubscriptionAccessDecision(
        boolean premium,
        SubscriptionStatus status,
        Instant validUntil
) {
}
