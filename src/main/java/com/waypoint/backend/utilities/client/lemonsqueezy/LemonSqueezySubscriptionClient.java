package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.waypoint.backend.model.subscription.ProviderSubscriptionSnapshot;

import java.util.List;

public interface LemonSqueezySubscriptionClient {
    List<ProviderSubscriptionSnapshot> listSubscriptions();

    default ProviderSubscriptionSnapshot skipTrial(String externalSubscriptionId) {
        throw new UnsupportedOperationException("Skipping a Lemon Squeezy trial is not implemented by this client");
    }

    default void cancelSubscription(String externalSubscriptionId) {
        throw new UnsupportedOperationException("Cancelling a Lemon Squeezy subscription is not implemented by this client");
    }
}
