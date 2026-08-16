package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.waypoint.backend.model.billing.ProviderPriceCatalog;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;

public interface LemonSqueezyClient {
    String createCheckout(UserEntity user, CheckoutPlan plan, String variantId);

    default ProviderPriceCatalog fetchPriceCatalog(String monthlyVariantId, String annualVariantId) {
        throw new UnsupportedOperationException("Pricing lookup is not implemented by this Lemon Squeezy client");
    }
}