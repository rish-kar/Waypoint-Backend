package com.waypoint.backend.service.subscription;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;

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
            case ACTIVE, PAUSED, PAST_DUE -> hasFutureRenewal(subscription, now)
                    ? new SubscriptionAccessDecision(true, status, subscription.getRenewsAt())
                    : new SubscriptionAccessDecision(false, status, null);
            case ON_TRIAL -> subscription.getTrialEndsAt() != null && subscription.getTrialEndsAt().isAfter(now)
                    ? new SubscriptionAccessDecision(true, status, subscription.getTrialEndsAt())
                    : new SubscriptionAccessDecision(false, status, null);
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

    private boolean hasFutureRenewal(SubscriptionEntity subscription, Instant now) {
        return subscription.getRenewsAt() != null && subscription.getRenewsAt().isAfter(now);
    }
}
