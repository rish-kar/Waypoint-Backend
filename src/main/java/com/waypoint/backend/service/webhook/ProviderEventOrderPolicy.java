package com.waypoint.backend.service.webhook;

import java.time.Instant;

final class ProviderEventOrderPolicy {
    boolean shouldApply(Instant lastProviderEventAt, Instant incomingProviderEventAt) {
        if (incomingProviderEventAt == null) {
            return lastProviderEventAt == null;
        }
        return lastProviderEventAt == null || incomingProviderEventAt.isAfter(lastProviderEventAt);
    }
}
