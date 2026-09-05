package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionServiceCandidateSelectionTests {
    @Test
    void singleUserResolutionSkipsRejectedCandidateAndUsesValidPremiumCandidate() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        SubscriptionAccessPolicy subscriptionAccessPolicy = mock(SubscriptionAccessPolicy.class);
        SpecialPremiumGrantRepository specialPremiumGrantRepository = mock(SpecialPremiumGrantRepository.class);
        SubscriptionService subscriptionService = new SubscriptionService(
                subscriptionRepository,
                subscriptionAccessPolicy,
                specialPremiumGrantRepository
        );

        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-05T06:30:00Z");
        when(specialPremiumGrantRepository.findByUserId(userId)).thenReturn(Optional.empty());

        SubscriptionEntity rejected = subscription(userId, CheckoutPlan.ANNUAL, now.plusSeconds(7200), now.plusSeconds(60));
        SubscriptionEntity valid = subscription(userId, CheckoutPlan.MONTHLY, now.plusSeconds(3600), now);

        when(subscriptionRepository.findCurrentPremiumCandidates(
                eq(userId),
                eq(now),
                eq(SubscriptionStatus.ON_TRIAL),
                anySet(),
                eq(SubscriptionStatus.CANCELLED),
                any(Pageable.class)
        )).thenReturn(List.of(rejected, valid));
        when(subscriptionAccessPolicy.evaluate(rejected, now))
                .thenReturn(new SubscriptionAccessDecision(false, SubscriptionStatus.ACTIVE, null));
        when(subscriptionAccessPolicy.evaluate(valid, now))
                .thenReturn(new SubscriptionAccessDecision(true, SubscriptionStatus.ACTIVE, valid.getRenewsAt()));

        SubscriptionSnapshot result = subscriptionService.current(userId, now);

        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_MONTHLY);
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.premium()).isTrue();
        assertThat(result.externalSubscriptionId()).isEqualTo(valid.getExternalSubscriptionId());
        assertThat(result.validUntil()).isEqualTo(valid.getRenewsAt());
    }

    private SubscriptionEntity subscription(
            UUID userId,
            CheckoutPlan plan,
            Instant renewsAt,
            Instant updatedAt
    ) {
        UserEntity user = new UserEntity();
        user.setId(userId);

        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlan(plan.name());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setExternalVariantId(plan == CheckoutPlan.MONTHLY ? "111" : "222");
        subscription.setExternalSubscriptionId("sub-" + plan.name().toLowerCase());
        subscription.setRenewsAt(renewsAt);
        subscription.setUpdatedAt(updatedAt);
        return subscription;
    }
}
