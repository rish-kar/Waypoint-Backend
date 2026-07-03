package com.waypoint.backend.billing;

import com.waypoint.backend.common.InvalidRequestException;
import com.waypoint.backend.subscription.CheckoutPlan;
import com.waypoint.backend.subscription.SubscriptionEntity;
import com.waypoint.backend.subscription.SubscriptionRepository;
import com.waypoint.backend.subscription.SubscriptionStatus;
import com.waypoint.backend.user.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.UUID;

@Service
public class BillingService {
    private final LemonSqueezyClient lemonSqueezyClient;
    private final LemonSqueezyProperties properties;
    private final SubscriptionRepository subscriptionRepository;

    public BillingService(
            LemonSqueezyClient lemonSqueezyClient,
            LemonSqueezyProperties properties,
            SubscriptionRepository subscriptionRepository
    ) {
        this.lemonSqueezyClient = lemonSqueezyClient;
        this.properties = properties;
        this.subscriptionRepository = subscriptionRepository;
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
        return subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(this::hasPremiumBillingStatus)
                .max(Comparator.comparing(SubscriptionEntity::getUpdatedAt))
                .map(subscription -> new BillingStatusResponse(
                        "PREMIUM",
                        subscription.getStatus().name(),
                        subscription.getExternalSubscriptionId(),
                        subscription.getRenewsAt(),
                        subscription.getEndsAt()
                ))
                .orElseGet(() -> new BillingStatusResponse("FREE", SubscriptionStatus.INACTIVE.name(), null, null, null));
    }

    private boolean hasPremiumBillingStatus(SubscriptionEntity subscription) {
        SubscriptionStatus status = subscription.getStatus();
        return status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.ON_TRIAL || status == SubscriptionStatus.CANCELLED;
    }
}
