package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAccessPolicy subscriptionAccessPolicy;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionAccessPolicy subscriptionAccessPolicy
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAccessPolicy = subscriptionAccessPolicy;
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot current(UUID userId) {
        return current(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot current(UUID userId, Instant now) {
        List<SubscriptionEntity> subscriptions = subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (subscriptions.isEmpty()) {
            return freeSnapshot(SubscriptionStatus.INACTIVE, null, now);
        }

        return subscriptions.stream()
                .map(subscription -> new Candidate(
                        subscription,
                        subscriptionAccessPolicy.evaluate(subscription, now)
                ))
                .filter(candidate -> candidate.decision().premium())
                .max(Comparator
                        .comparing((Candidate candidate) -> comparableValidUntil(candidate.decision()))
                        .thenComparing(candidate -> comparableUpdatedAt(candidate.subscription())))
                .map(candidate -> premiumSnapshot(candidate, now))
                .orElseGet(() -> {
                    SubscriptionEntity latest = subscriptions.getFirst();
                    SubscriptionStatus status = latest.getStatus() == null
                            ? SubscriptionStatus.INACTIVE
                            : latest.getStatus();
                    return freeSnapshot(status, latest, now);
                });
    }

    private SubscriptionSnapshot premiumSnapshot(Candidate candidate, Instant checkedAt) {
        SubscriptionEntity subscription = candidate.subscription();
        SubscriptionAccessDecision decision = candidate.decision();
        return new SubscriptionSnapshot(
                planCodeFor(subscription),
                decision.status(),
                true,
                subscription.getExternalSubscriptionId(),
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

    private Instant comparableValidUntil(SubscriptionAccessDecision decision) {
        return decision.validUntil() == null ? Instant.MAX : decision.validUntil();
    }

    private Instant comparableUpdatedAt(SubscriptionEntity subscription) {
        return subscription.getUpdatedAt() == null ? Instant.EPOCH : subscription.getUpdatedAt();
    }

    private record Candidate(
            SubscriptionEntity subscription,
            SubscriptionAccessDecision decision
    ) {
    }
}
