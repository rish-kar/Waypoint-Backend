package com.waypoint.backend.subscription;

import com.waypoint.backend.billing.LemonSqueezyProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
public class SubscriptionAccessPolicy {
    private final LemonSqueezyProperties properties;

    public SubscriptionAccessPolicy(LemonSqueezyProperties properties) {
        this.properties = properties;
    }

    public SubscriptionAccessDecision evaluate(SubscriptionEntity subscription, Instant now) {
        SubscriptionStatus status = subscription.getStatus() == null ? SubscriptionStatus.UNKNOWN : subscription.getStatus();
        if (!hasRecognizedPremiumPlanAndVariant(subscription)) {
            return new SubscriptionAccessDecision(false, status, null);
        }
        return switch (status) {
            case ACTIVE, ON_TRIAL -> new SubscriptionAccessDecision(true, status, subscription.getRenewsAt());
            case CANCELLED -> subscription.getEndsAt() != null && subscription.getEndsAt().isAfter(now)
                    ? new SubscriptionAccessDecision(true, status, subscription.getEndsAt())
                    : new SubscriptionAccessDecision(false, status, null);
            default -> new SubscriptionAccessDecision(false, status, null);
        };
    }

    public String planForVariant(String variantId) {
        if (StringUtils.hasText(variantId) && variantId.equals(properties.monthlyVariantId())) {
            return CheckoutPlan.MONTHLY.name();
        }
        if (StringUtils.hasText(variantId) && variantId.equals(properties.annualVariantId())) {
            return CheckoutPlan.ANNUAL.name();
        }
        return "UNKNOWN";
    }

    private boolean hasRecognizedPremiumPlanAndVariant(SubscriptionEntity subscription) {
        String expectedPlan = planForVariant(subscription.getExternalVariantId());
        return !"UNKNOWN".equals(expectedPlan) && expectedPlan.equals(subscription.getPlan());
    }
}
