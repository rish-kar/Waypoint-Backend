package com.waypoint.backend.service.billing;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class BillingService {
    private final LemonSqueezyClient lemonSqueezyClient;
    private final LemonSqueezyProperties properties;
    private final SubscriptionService subscriptionService;

    public BillingService(
            LemonSqueezyClient lemonSqueezyClient,
            LemonSqueezyProperties properties,
            SubscriptionService subscriptionService
    ) {
        this.lemonSqueezyClient = lemonSqueezyClient;
        this.properties = properties;
        this.subscriptionService = subscriptionService;
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

    public BillingStatusResponse billingStatus(UUID userId) {
        SubscriptionSnapshot subscription = subscriptionService.current(userId);
        if (!subscription.premium()) {
            return new BillingStatusResponse(
                    "FREE",
                    subscription.status().name(),
                    null,
                    null,
                    null
            );
        }
        return new BillingStatusResponse(
                "PREMIUM",
                subscription.status().name(),
                subscription.externalSubscriptionId(),
                subscription.renewsAt(),
                subscription.endsAt()
        );
    }
}
