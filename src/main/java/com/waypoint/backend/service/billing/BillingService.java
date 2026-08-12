package com.waypoint.backend.service.billing;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.service.subscription.SubscriptionAccessPolicy;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService {
    private final LemonSqueezyClient lemonSqueezyClient;
    private final LemonSqueezyProperties properties;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAccessPolicy subscriptionAccessPolicy;
    private final PlanRepository planRepository;

    public BillingService(
            LemonSqueezyClient lemonSqueezyClient,
            LemonSqueezyProperties properties,
            SubscriptionRepository subscriptionRepository,
            SubscriptionAccessPolicy subscriptionAccessPolicy,
            PlanRepository planRepository
    ) {
        this.lemonSqueezyClient = lemonSqueezyClient;
        this.properties = properties;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAccessPolicy = subscriptionAccessPolicy;
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> availablePlans() {
        return planRepository.findByActiveTrueAndPremiumTrueOrderByPriceCentsAsc().stream()
                .map(PlanResponse::from)
                .toList();
    }

    public String createCheckout(UserEntity user, CheckoutPlan plan) {
        if (plan == null) {
            throw new InvalidRequestException("plan is required");
        }
        String variantId = switch (plan) {
            case MONTHLY -> properties.monthlyVariantId();
            case ANNUAL -> properties.annualVariantId();
        };
        if (!StringUtils.hasText(variantId)) {
            throw new InvalidRequestException("Requested billing plan is not configured");
        }
        return lemonSqueezyClient.createCheckout(user, plan, variantId);
    }

    @Transactional(readOnly = true)
    public BillingStatusResponse billingStatus(UUID userId) {
        Instant now = Instant.now();
        List<SubscriptionEntity> subscriptions = subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return subscriptions.stream()
                .filter(subscription -> subscriptionAccessPolicy.evaluate(subscription, now).premium())
                .max(Comparator.comparing(SubscriptionEntity::getUpdatedAt))
                .map(subscription -> new BillingStatusResponse(
                        "PREMIUM",
                        planCodeFor(subscription),
                        subscription.getStatus().name(),
                        subscription.getExternalSubscriptionId(),
                        subscription.getRenewsAt(),
                        subscription.getEndsAt()
                ))
                .orElseGet(() -> new BillingStatusResponse(
                        "FREE",
                        PlanCode.FREE,
                        latestStatus(subscriptions),
                        null,
                        null,
                        null
                ));
    }

    private String latestStatus(List<SubscriptionEntity> subscriptions) {
        if (subscriptions.isEmpty()) {
            return SubscriptionStatus.INACTIVE.name();
        }
        SubscriptionStatus status = subscriptions.getFirst().getStatus();
        return status == null ? SubscriptionStatus.INACTIVE.name() : status.name();
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
