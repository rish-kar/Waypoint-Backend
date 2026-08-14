package com.waypoint.backend.service.webhook;

import java.time.Instant;

final class ProviderEventOrderPolicy {
    boolean shouldApply(Instant lastProviderEventAt, Instant incomingProviderEventAt) {
        return incomingProviderEventAt != null
                && (lastProviderEventAt == null || !incomingProviderEventAt.isBefore(lastProviderEventAt));
    }
}
