package com.waypoint.backend.subscription;

import java.time.Instant;

public record SubscriptionAccessDecision(
        boolean premium,
        SubscriptionStatus status,
        Instant validUntil
) {
}
