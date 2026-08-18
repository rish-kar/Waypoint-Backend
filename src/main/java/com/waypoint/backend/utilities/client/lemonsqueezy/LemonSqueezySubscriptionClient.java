package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.waypoint.backend.model.subscription.ProviderSubscriptionSnapshot;

import java.util.List;

public interface LemonSqueezySubscriptionClient {
    List<ProviderSubscriptionSnapshot> listSubscriptions();
}
