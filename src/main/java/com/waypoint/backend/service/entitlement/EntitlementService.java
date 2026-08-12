package com.waypoint.backend.service.entitlement;

import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.service.subscription.SubscriptionAccessPolicy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class EntitlementService {
    private static final List<String> FREE_FEATURES = List.of("instant-tab-search");
    private static final List<String> PREMIUM_FEATURES = List.of(
            "instant-tab-search",
            "duplicate-tabs",
            "saved-workspaces",
            "tab-tasks",
            "snooze-tabs",
            "smart-tab-groups",
            "calendar-slack-integrations",
            "session-timeline",
            "ai-summary"
    );

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAccessPolicy subscriptionAccessPolicy;

    public EntitlementService(SubscriptionRepository subscriptionRepository, SubscriptionAccessPolicy subscriptionAccessPolicy) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAccessPolicy = subscriptionAccessPolicy;
    }

    @Transactional(readOnly = true)
    public EntitlementResponse currentEntitlement(UUID userId, boolean includeCheckedAt) {
        Instant now = Instant.now();
        List<SubscriptionEntity> subscriptions = subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return subscriptions.stream()
                .map(subscription -> subscriptionAccessPolicy.evaluate(subscription, now))
                .filter(SubscriptionAccessDecision::premium)
                .max(Comparator.comparing(decision -> decision.validUntil() == null ? Instant.MAX : decision.validUntil()))
                .map(decision -> premiumResponse(decision, includeCheckedAt ? now : null))
                .orElseGet(() -> freeResponse(latestStatus(subscriptions), includeCheckedAt ? now : null));
    }

    private EntitlementResponse premiumResponse(SubscriptionAccessDecision decision, Instant checkedAt) {
        return new EntitlementResponse(
                "PREMIUM",
                decision.status().name(),
                true,
                PREMIUM_FEATURES,
                decision.validUntil(),
                checkedAt
        );
    }

    private EntitlementResponse freeResponse(String status, Instant checkedAt) {
        return new EntitlementResponse(
                "FREE",
                status,
                false,
                FREE_FEATURES,
                null,
                checkedAt
        );
    }

    private String latestStatus(List<SubscriptionEntity> subscriptions) {
        if (subscriptions.isEmpty()) {
            return SubscriptionStatus.INACTIVE.name();
        }
        SubscriptionStatus status = subscriptions.getFirst().getStatus();
        return status == null ? SubscriptionStatus.INACTIVE.name() : status.name();
    }
}
