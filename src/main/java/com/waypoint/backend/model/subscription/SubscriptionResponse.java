package com.waypoint.backend.model.subscription;

import com.waypoint.backend.model.plan.PlanCode;

import java.time.Instant;

public record SubscriptionResponse(
        PlanCode plan,
        SubscriptionStatus status,
        boolean premium,
        String externalSubscriptionId,
        Instant trialEndsAt,
        Instant renewsAt,
        Instant endsAt,
        Instant validUntil,
        Instant checkedAt
) {
    public static SubscriptionResponse from(SubscriptionSnapshot snapshot) {
        return new SubscriptionResponse(
                snapshot.planCode(),
                snapshot.status(),
                snapshot.premium(),
                snapshot.externalSubscriptionId(),
                snapshot.trialEndsAt(),
                snapshot.renewsAt(),
                snapshot.endsAt(),
                snapshot.validUntil(),
                snapshot.checkedAt()
        );
    }
}
