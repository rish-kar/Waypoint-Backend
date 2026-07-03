package com.waypoint.backend.entitlement;

import com.waypoint.backend.subscription.SubscriptionEntity;
import com.waypoint.backend.subscription.SubscriptionRepository;
import com.waypoint.backend.subscription.SubscriptionStatus;
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

    public EntitlementService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public EntitlementResponse currentEntitlement(UUID userId, boolean includeCheckedAt) {
        Instant now = Instant.now();
        List<SubscriptionEntity> subscriptions = subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return subscriptions.stream()
                .map(subscription -> evaluate(subscription, now))
                .filter(Evaluation::premium)
                .max(Comparator.comparing(evaluation -> evaluation.validUntil() == null ? Instant.MAX : evaluation.validUntil()))
                .map(evaluation -> premiumResponse(evaluation, includeCheckedAt ? now : null))
                .orElseGet(() -> freeResponse(latestStatus(subscriptions), includeCheckedAt ? now : null));
    }

    private Evaluation evaluate(SubscriptionEntity subscription, Instant now) {
        SubscriptionStatus status = subscription.getStatus() == null ? SubscriptionStatus.UNKNOWN : subscription.getStatus();
        return switch (status) {
            case ACTIVE, ON_TRIAL -> new Evaluation(true, status, subscription.getRenewsAt());
            case CANCELLED -> subscription.getEndsAt() != null && subscription.getEndsAt().isAfter(now)
                    ? new Evaluation(true, status, subscription.getEndsAt())
                    : new Evaluation(false, status, null);
            default -> new Evaluation(false, status, null);
        };
    }

    private EntitlementResponse premiumResponse(Evaluation evaluation, Instant checkedAt) {
        return new EntitlementResponse(
                "PREMIUM",
                evaluation.status().name(),
                true,
                PREMIUM_FEATURES,
                evaluation.validUntil(),
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

    private record Evaluation(boolean premium, SubscriptionStatus status, Instant validUntil) {
    }
}
