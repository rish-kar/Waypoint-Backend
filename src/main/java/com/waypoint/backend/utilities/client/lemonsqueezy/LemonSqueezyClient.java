package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;

public interface LemonSqueezyClient {
    String createCheckout(UserEntity user, CheckoutPlan plan, String variantId);
}
