package com.waypoint.backend.billing;

import java.time.Instant;

public record BillingStatusResponse(
        String plan,
        String status,
        String externalSubscriptionId,
        Instant renewsAt,
        Instant endsAt
) {
}
