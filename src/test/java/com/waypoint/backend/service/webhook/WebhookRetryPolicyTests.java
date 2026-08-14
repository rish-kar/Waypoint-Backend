package com.waypoint.backend.service.webhook;

import com.waypoint.backend.model.webhook.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookRetryPolicyTests {
    private final WebhookRetryPolicy policy = new WebhookRetryPolicy();
    private final Instant now = Instant.parse("2026-08-14T18:00:00Z");

    @Test
    void retriesFailedEvents() {
        assertTrue(policy.shouldClaim(ProcessingStatus.FAILED, now, now));
    }

    @Test
    void doesNotRetryFreshReceivedEvents() {
        assertFalse(policy.shouldClaim(ProcessingStatus.RECEIVED, now.minusSeconds(60), now));
    }

    @Test
    void retriesStaleReceivedEvents() {
        assertTrue(policy.shouldClaim(ProcessingStatus.RECEIVED, now.minusSeconds(300), now));
        assertTrue(policy.shouldClaim(ProcessingStatus.RECEIVED, now.minusSeconds(301), now));
    }

    @Test
    void retriesLegacyReceivedEventsWithoutStartTime() {
        assertTrue(policy.shouldClaim(ProcessingStatus.RECEIVED, null, now));
    }

    @Test
    void neverRetriesProcessedEvents() {
        assertFalse(policy.shouldClaim(ProcessingStatus.PROCESSED, now.minusSeconds(600), now));
    }
}
