package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    void blocksCheckoutForActiveOrTrialSubscription() {
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(subscription(SubscriptionStatus.ACTIVE, null)));
        assertThat(subscriptionService.hasCheckoutBlockingSubscription(userId, now)).isTrue();

        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(subscription(SubscriptionStatus.ON_TRIAL, null)));
        assertThat(subscriptionService.hasCheckoutBlockingSubscription(userId, now)).isTrue();
    }

    @Test
    void blocksCancelledSubscriptionUntilItsPaidPeriodEnds() {
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(subscription(SubscriptionStatus.CANCELLED, now.plusSeconds(3600))));

        assertThat(subscriptionService.hasCheckoutBlockingSubscription(userId, now)).isTrue();
    }

    @Test
    void allowsCheckoutAfterCancelledOrExpiredSubscriptionEnds() {
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(
                        subscription(SubscriptionStatus.CANCELLED, now.minusSeconds(1)),
                        subscription(SubscriptionStatus.EXPIRED, null)
                ));

        assertThat(subscriptionService.hasCheckoutBlockingSubscription(userId, now)).isFalse();
    }

    @Test
    void blocksUnknownProviderStateToAvoidAccidentalDoubleBilling() {
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of(subscription(SubscriptionStatus.UNKNOWN, null)));

        assertThat(subscriptionService.hasCheckoutBlockingSubscription(userId, now)).isTrue();
    }

    private SubscriptionEntity subscription(SubscriptionStatus status, Instant endsAt) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setExternalSubscriptionId("sub_123");
        subscription.setStatus(status);
        subscription.setEndsAt(endsAt);
        return subscription;
    }
}
