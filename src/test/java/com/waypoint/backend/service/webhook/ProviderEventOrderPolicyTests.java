package com.waypoint.backend.service.webhook;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderEventOrderPolicyTests {
    private final ProviderEventOrderPolicy policy = new ProviderEventOrderPolicy();
    private final Instant now = Instant.parse("2026-08-15T10:00:00Z");

    @Test
    void acceptsFirstTimestampedEvent() {
        assertThat(policy.shouldApply(null, now)).isTrue();
    }

    @Test
    void acceptsLegacyEventBeforeAnyTimestampedStateExists() {
        assertThat(policy.shouldApply(null, null)).isTrue();
    }

    @Test
    void acceptsNewerEvent() {
        assertThat(policy.shouldApply(now, now.plusSeconds(1))).isTrue();
    }

    @Test
    void rejectsOlderOrEqualEvent() {
        assertThat(policy.shouldApply(now, now.minusSeconds(1))).isFalse();
        assertThat(policy.shouldApply(now, now)).isFalse();
    }

    @Test
    void rejectsMissingTimestampAfterTimestampedStateExists() {
        assertThat(policy.shouldApply(now, null)).isFalse();
    }
}
