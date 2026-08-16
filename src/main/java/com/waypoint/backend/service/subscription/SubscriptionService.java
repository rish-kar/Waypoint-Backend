package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SubscriptionService {
    private static final Set<SubscriptionStatus> RENEWING_STATUSES = Set.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAUSED,
            SubscriptionStatus.PAST_DUE
    );
    private static final Set<SubscriptionStatus> CHECKOUT_BLOCKING_STATUSES = Set.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.ON_TRIAL,
            SubscriptionStatus.PAUSED,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.UNPAID,
            SubscriptionStatus.UNKNOWN
    );

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAccessPolicy subscriptionAccessPolicy;
    private final SpecialPremiumGrantRepository specialPremiumGrantRepository;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionAccessPolicy subscriptionAccessPolicy,
            SpecialPremiumGrantRepository specialPremiumGrantRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAccessPolicy = subscriptionAccessPolicy;
        this.specialPremiumGrantRepository = specialPremiumGrantRepository;
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot current(UUID userId) {
        return current(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot current(UUID userId, Instant now) {
        SpecialPremiumGrantEntity specialGrant = specialPremiumGrantRepository.findByUserId(userId)
                .filter(grant -> isActiveSpecialGrant(grant, now))
                .orElse(null);
        if (specialGrant != null) {
            return new SubscriptionSnapshot(
                    PlanCode.PREMIUM_SPECIAL,
                    SubscriptionStatus.PREMIUM_SPECIAL,
                    true,
                    null,
                    null,
                    null,
                    null,
                    specialGrant.getValidUntil(),
                    now
            );
        }

        return currentBilling(userId, now);
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot currentBilling(UUID userId) {
        return currentBilling(userId, Instant.now());
    }

    SubscriptionSnapshot currentBilling(UUID userId, Instant now) {
        List<SubscriptionEntity> premiumCandidates = subscriptionRepository.findCurrentPremiumCandidates(
                userId,
                now,
                SubscriptionStatus.ON_TRIAL,
                RENEWING_STATUSES,
                SubscriptionStatus.CANCELLED,
                PageRequest.of(0, 1)
        );
        if (!premiumCandidates.isEmpty()) {
            SubscriptionEntity subscription = premiumCandidates.getFirst();
            SubscriptionAccessDecision decision = subscriptionAccessPolicy.evaluate(subscription, now);
            if (decision.premium()) {
                return premiumSnapshot(subscription, decision, now);
            }
        }

        SubscriptionEntity latest = subscriptionRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);
        if (latest == null) {
            return freeSnapshot(SubscriptionStatus.INACTIVE, null, now);
        }
        SubscriptionStatus status = latest.getStatus() == null
                ? SubscriptionStatus.INACTIVE
                : latest.getStatus();
        return freeSnapshot(status, latest, now);
    }

    @Transactional(readOnly = true)
    public boolean hasCheckoutBlockingSubscription(UUID userId) {
        return hasCheckoutBlockingSubscription(userId, Instant.now());
    }

    boolean hasCheckoutBlockingSubscription(UUID userId, Instant now) {
        return subscriptionRepository.existsCheckoutBlockingSubscription(
                userId,
                now,
                CHECKOUT_BLOCKING_STATUSES,
                SubscriptionStatus.CANCELLED
        );
    }

    private boolean isActiveSpecialGrant(SpecialPremiumGrantEntity grant, Instant now) {
        return grant.isActive() && (grant.getValidUntil() == null || grant.getValidUntil().isAfter(now));
    }

    private SubscriptionSnapshot premiumSnapshot(
            SubscriptionEntity subscription,
            SubscriptionAccessDecision decision,
            Instant checkedAt
    ) {
        return new SubscriptionSnapshot(
                planCodeFor(subscription),
                decision.status(),
                true,
                subscription.getExternalSubscriptionId(),
                subscription.getTrialEndsAt(),
                subscription.getRenewsAt(),
                subscription.getEndsAt(),
                decision.validUntil(),
                checkedAt
        );
    }

    private SubscriptionSnapshot freeSnapshot(
            SubscriptionStatus status,
            SubscriptionEntity subscription,
            Instant checkedAt
    ) {
        return new SubscriptionSnapshot(
                PlanCode.FREE,
                status,
                false,
                subscription == null ? null : subscription.getExternalSubscriptionId(),
                subscription == null ? null : subscription.getTrialEndsAt(),
                subscription == null ? null : subscription.getRenewsAt(),
                subscription == null ? null : subscription.getEndsAt(),
                null,
                checkedAt
        );
    }

    private PlanCode planCodeFor(SubscriptionEntity subscription) {
        if (CheckoutPlan.ANNUAL.name().equals(subscription.getPlan())) {
            return PlanCode.PREMIUM_ANNUAL;
        }
        if (CheckoutPlan.MONTHLY.name().equals(subscription.getPlan())) {
            return PlanCode.PREMIUM_MONTHLY;
        }
        return PlanCode.FREE;
    }
}