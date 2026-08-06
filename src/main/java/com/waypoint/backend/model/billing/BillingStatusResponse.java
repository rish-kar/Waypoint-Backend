package com.waypoint.backend.model.billing;

import java.time.Instant;

public record BillingStatusResponse(
        String plan,
        String status,
        String externalSubscriptionId,
        Instant renewsAt,
        Instant endsAt
) {
}
