package com.waypoint.backend.service.webhook;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderEventOrderPolicyTests {
    private final ProviderEventOrderPolicy policy = new ProviderEventOrderPolicy();
    private final Instant current = Instant.parse("2026-08-14T18:00:00Z");

    @Test
    void acceptsFirstEvent() {
        assertTrue(policy.shouldApply(null, current));
    }

    @Test
    void acceptsEqualOrNewerEvents() {
        assertTrue(policy.shouldApply(current, current));
        assertTrue(policy.shouldApply(current, current.plusSeconds(1)));
    }

    @Test
    void rejectsOlderEvents() {
        assertFalse(policy.shouldApply(current, current.minusSeconds(1)));
    }

    @Test
    void rejectsMissingEventTimestamp() {
        assertFalse(policy.shouldApply(current, null));
    }
}
