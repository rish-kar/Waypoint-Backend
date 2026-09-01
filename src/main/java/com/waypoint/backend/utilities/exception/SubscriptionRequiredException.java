package com.waypoint.backend.utilities.exception;

import org.springframework.http.HttpStatus;

public class SubscriptionRequiredException extends ApiException {
    public SubscriptionRequiredException(String feature) {
        super(
                HttpStatus.FORBIDDEN,
                "SUBSCRIPTION_REQUIRED",
                "An active Waypoint subscription is required for feature: " + feature
        );
    }
}
