package com.waypoint.backend.model.subscription;

import com.waypoint.backend.model.plan.PlanCode;

import java.time.Instant;

public record SubscriptionSnapshot(
        PlanCode planCode,
        SubscriptionStatus status,
        boolean premium,
        String externalSubscriptionId,
        Instant renewsAt,
        Instant endsAt,
        Instant validUntil,
        Instant checkedAt
) {
}
