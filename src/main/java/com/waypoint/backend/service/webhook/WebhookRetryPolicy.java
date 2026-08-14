package com.waypoint.backend.service.webhook;

import com.waypoint.backend.model.webhook.ProcessingStatus;

import java.time.Duration;
import java.time.Instant;

final class WebhookRetryPolicy {
    static final Duration STALE_RECEIVED_AFTER = Duration.ofMinutes(5);

    boolean shouldClaim(ProcessingStatus status, Instant processingStartedAt, Instant now) {
        if (status == ProcessingStatus.FAILED) {
            return true;
        }
        if (status != ProcessingStatus.RECEIVED) {
            return false;
        }
        return processingStartedAt == null
                || !processingStartedAt.isAfter(now.minus(STALE_RECEIVED_AFTER));
    }
}
