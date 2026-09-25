package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;

import java.util.Optional;
import java.util.UUID;

public interface LemonSqueezyClient {
    String createCheckout(UserEntity user, CheckoutPlan plan, String variantId);

    default String createCheckout(UserEntity user, CheckoutPlan plan, String variantId, UUID intentId) {
        return createCheckout(user, plan, variantId);
    }

    default Optional<String> findCheckoutByIntent(String variantId, UUID intentId) {
        return Optional.empty();
    }

}
