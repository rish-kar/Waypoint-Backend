package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;

import org.junit.jupiter.api.BeforeEach;
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

class SubscriptionServiceTests {
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionAccessPolicy subscriptionAccessPolicy;
    private SpecialPremiumGrantRepository specialPremiumGrantRepository;
    private SubscriptionService subscriptionService;
    private UUID userId;
    private Instant now;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        subscriptionAccessPolicy = mock(SubscriptionAccessPolicy.class);
        specialPremiumGrantRepository = mock(SpecialPremiumGrantRepository.class);
        subscriptionService = new SubscriptionService(
                subscriptionRepository,
                subscriptionAccessPolicy,
                specialPremiumGrantRepository
        );
        userId = UUID.randomUUID();
        now = Instant.parse("2026-08-12T08:00:00Z");
        when(specialPremiumGrantRepository.findByUserId(userId)).thenReturn(Optional.empty());
    }

    @Test
    void returnsFreeInactiveWhenUserHasNoSubscription() {
        premiumCandidates(List.of());
        when(subscriptionRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(Optional.empty());

        SubscriptionSnapshot result = subscriptionService.current(userId, now);

        assertThat(result.planCode()).isEqualTo(PlanCode.FREE);
        assertThat(result.status()).isEqualTo(SubscriptionStatus.INACTIVE);
        assertThat(result.premium()).isFalse();
        assertThat(result.checkedAt()).isEqualTo(now);
    }

    @Test
    void returnsPremiumSpecialBeforePaidSubscription() {
        SpecialPremiumGrantEntity grant = new SpecialPremiumGrantEntity();
        grant.setActive(true);
        grant.setValidUntil(now.plusSeconds(86400));
        when(specialPremiumGrantRepository.findByUserId(userId)).thenReturn(Optional.of(grant));

        SubscriptionSnapshot result = subscriptionService.current(userId, now);

        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_SPECIAL);
        assertThat(result.status()).isEqualTo(SubscriptionStatus.PREMIUM_SPECIAL);
        assertThat(result.premium()).isTrue();
        assertThat(result.validUntil()).isEqualTo(grant.getValidUntil());
        assertThat(result.trialEndsAt()).isNull();
    }

    @Test
    void currentBillingIgnoresPremiumSpecialAndReturnsOnTrialSubscription() {
        SpecialPremiumGrantEntity grant = new SpecialPremiumGrantEntity();
        grant.setActive(true);
        grant.setValidUntil(now.plusSeconds(86400));
        when(specialPremiumGrantRepository.findByUserId(userId)).thenReturn(Optional.of(grant));

        SubscriptionEntity trial = subscription(CheckoutPlan.MONTHLY, SubscriptionStatus.ON_TRIAL, now.minusSeconds(60));
        Instant trialEndsAt = now.plusSeconds(604800);
        trial.setTrialEndsAt(trialEndsAt);
        trial.setRenewsAt(trialEndsAt);
        premiumCandidates(List.of(trial));
        when(subscriptionAccessPolicy.evaluate(trial, now))
                .thenReturn(new SubscriptionAccessDecision(true, SubscriptionStatus.ON_TRIAL, trialEndsAt));

        SubscriptionSnapshot result = subscriptionService.currentBilling(userId, now);

        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_MONTHLY);
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ON_TRIAL);
        assertThat(result.premium()).isTrue();
        assertThat(result.externalSubscriptionId()).isEqualTo(trial.getExternalSubscriptionId());
        assertThat(result.trialEndsAt()).isEqualTo(trialEndsAt);
        assertThat(result.validUntil()).isEqualTo(trialEndsAt);
    }

    @Test
    void ignoresExpiredPremiumSpecialGrant() {
        SpecialPremiumGrantEntity grant = new SpecialPremiumGrantEntity();
        grant.setActive(true);
        grant.setValidUntil(now.minusSeconds(1));
        when(specialPremiumGrantRepository.findByUserId(userId)).thenReturn(Optional.of(grant));
        premiumCandidates(List.of());
        when(subscriptionRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(Optional.empty());

        SubscriptionSnapshot result = subscriptionService.current(userId, now);

        assertThat(result.planCode()).isEqualTo(PlanCode.FREE);
        assertThat(result.premium()).isFalse();
    }

    @Test
    void returnsMonthlyPremiumForActiveMonthlySubscription() {
        SubscriptionEntity monthly = subscription(CheckoutPlan.MONTHLY, SubscriptionStatus.ACTIVE, now.minusSeconds(60));
        Instant renewsAt = now.plusSeconds(3600);
        monthly.setRenewsAt(renewsAt);
        premiumCandidates(List.of(monthly));
        when(subscriptionAccessPolicy.evaluate(monthly, now))
                .thenReturn(new SubscriptionAccessDecision(true, SubscriptionStatus.ACTIVE, renewsAt));

        SubscriptionSnapshot result = subscriptionService.current(userId, now);

        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_MONTHLY);
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.premium()).isTrue();
        assertThat(result.validUntil()).isEqualTo(renewsAt);
    }

    @Test
    void returnsAnnualPremiumForCancelledSubscriptionBeforeEndDate() {
        SubscriptionEntity annual = subscription(CheckoutPlan.ANNUAL, SubscriptionStatus.CANCELLED, now.minusSeconds(60));
        Instant endsAt = now.plusSeconds(7200);
        annual.setEndsAt(endsAt);
        premiumCandidates(List.of(annual));
        when(subscriptionAccessPolicy.evaluate(annual, now))
                .thenReturn(new SubscriptionAccessDecision(true, SubscriptionStatus.CANCELLED, endsAt));

        SubscriptionSnapshot result = subscriptionService.current(userId, now);

        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_ANNUAL);
        assertThat(result.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(result.premium()).isTrue();
        assertThat(result.validUntil()).isEqualTo(endsAt);
    }

    @Test
    void fallsBackToFreeWhenLatestSubscriptionHasNoPremiumAccess() {
        SubscriptionEntity expired = subscription(CheckoutPlan.MONTHLY, SubscriptionStatus.EXPIRED, now.minusSeconds(60));
        premiumCandidates(List.of());
        when(subscriptionRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(Optional.of(expired));

        SubscriptionSnapshot result = subscriptionService.current(userId, now);

        assertThat(result.planCode()).isEqualTo(PlanCode.FREE);
        assertThat(result.status()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(result.premium()).isFalse();
        assertThat(result.externalSubscriptionId()).isEqualTo(expired.getExternalSubscriptionId());
    }

    @Test
    void selectsPremiumSubscriptionWithLongestRemainingAccess() {
        SubscriptionEntity annual = subscription(CheckoutPlan.ANNUAL, SubscriptionStatus.CANCELLED, now.minusSeconds(120));
        Instant annualUntil = now.plusSeconds(86400);
        annual.setEndsAt(annualUntil);
        premiumCandidates(List.of(annual));
        when(subscriptionAccessPolicy.evaluate(annual, now))
                .thenReturn(new SubscriptionAccessDecision(true, SubscriptionStatus.CANCELLED, annualUntil));

        SubscriptionSnapshot result = subscriptionService.current(userId, now);

        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_ANNUAL);
        assertThat(result.externalSubscriptionId()).isEqualTo(annual.getExternalSubscriptionId());
        assertThat(result.validUntil()).isEqualTo(annualUntil);
    }

    private void premiumCandidates(List<SubscriptionEntity> candidates) {
        when(subscriptionRepository.findCurrentPremiumCandidates(
                eq(userId),
                eq(now),
                eq(SubscriptionStatus.ON_TRIAL),
                anySet(),
                eq(SubscriptionStatus.CANCELLED),
                any(Pageable.class)
        )).thenReturn(candidates);
    }

    private SubscriptionEntity subscription(
            CheckoutPlan plan,
            SubscriptionStatus status,
            Instant updatedAt
    ) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlan(plan.name());
        subscription.setStatus(status);
        subscription.setExternalVariantId(plan == CheckoutPlan.MONTHLY ? "111" : "222");
        subscription.setExternalSubscriptionId("sub-" + plan.name().toLowerCase());
        subscription.setUpdatedAt(updatedAt);
        return subscription;
    }
}
