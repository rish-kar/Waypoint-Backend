package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionCheckoutGuardTests {
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionService subscriptionService;
    private UUID userId;
    private Instant now;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        subscriptionService = new SubscriptionService(
                subscriptionRepository,
                mock(SubscriptionAccessPolicy.class),
                mock(SpecialPremiumGrantRepository.class)
        );
        userId = UUID.randomUUID();
        now = Instant.parse("2026-08-15T04:30:00Z");
    }

    @Test
    void blocksCheckoutForLiveOrRecoveringSubscriptions() {
        when(subscriptionRepository.existsCheckoutBlockingSubscription(
                eq(userId),
                eq(now),
                anySet(),
                eq(SubscriptionStatus.CANCELLED)
        )).thenReturn(true);

        assertThat(subscriptionService.hasCheckoutBlockingSubscription(userId, now)).isTrue();
    }

    @Test
    void blocksCancelledSubscriptionUntilItsPaidPeriodEnds() {
        when(subscriptionRepository.existsCheckoutBlockingSubscription(
                eq(userId),
                eq(now),
                anySet(),
                eq(SubscriptionStatus.CANCELLED)
        )).thenReturn(true);

        assertThat(subscriptionService.hasCheckoutBlockingSubscription(userId, now)).isTrue();
    }

    @Test
    void allowsCheckoutAfterCancelledOrExpiredSubscriptionEnds() {
        when(subscriptionRepository.existsCheckoutBlockingSubscription(
                eq(userId),
                eq(now),
                anySet(),
                eq(SubscriptionStatus.CANCELLED)
        )).thenReturn(false);

        assertThat(subscriptionService.hasCheckoutBlockingSubscription(userId, now)).isFalse();
    }
}
