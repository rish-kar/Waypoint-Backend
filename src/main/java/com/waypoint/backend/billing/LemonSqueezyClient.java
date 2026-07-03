package com.waypoint.backend.billing;

import com.waypoint.backend.subscription.CheckoutPlan;
import com.waypoint.backend.user.UserEntity;

public interface LemonSqueezyClient {
    String createCheckout(UserEntity user, CheckoutPlan plan, String variantId);
}
