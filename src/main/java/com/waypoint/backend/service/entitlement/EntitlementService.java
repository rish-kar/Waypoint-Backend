package com.waypoint.backend.service.entitlement;

import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.entitlement.FeatureCode;
import com.waypoint.backend.model.entitlement.FeatureEntitlementResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.SubscriptionRequiredException;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EntitlementService {
    private final SubscriptionService subscriptionService;
    private final FeatureCatalog featureCatalog;

    public EntitlementService(
            SubscriptionService subscriptionService,
            FeatureCatalog featureCatalog
    ) {
        this.subscriptionService = subscriptionService;
        this.featureCatalog = featureCatalog;
    }

    @Transactional(readOnly = true)
    public EntitlementResponse currentEntitlement(UUID userId, boolean includeCheckedAt) {
        SubscriptionSnapshot subscription = subscriptionService.current(userId);
        return new EntitlementResponse(
                entitlementPlan(subscription),
                subscription.status().name(),
                subscription.premium(),
                featureCatalog.featuresFor(subscription.planCode()),
                subscription.validUntil(),
                includeCheckedAt ? subscription.checkedAt() : null
        );
    }

    @Transactional(readOnly = true)
    public FeatureEntitlementResponse featureAccess(UUID userId, String featureValue) {
        FeatureCode feature = FeatureCode.fromValue(featureValue)
                .orElseThrow(() -> new InvalidRequestException("Unknown feature: " + featureValue));
        SubscriptionSnapshot subscription = subscriptionService.current(userId);
        return new FeatureEntitlementResponse(
                feature.value(),
                featureCatalog.hasFeature(subscription.planCode(), feature),
                entitlementPlan(subscription),
                subscription.status().name(),
                subscription.validUntil(),
                subscription.checkedAt()
        );
    }

    @Transactional(readOnly = true)
    public boolean hasFeature(UUID userId, FeatureCode feature) {
        if (userId == null) {
            return false;
        }
        SubscriptionSnapshot subscription = subscriptionService.current(userId);
        return featureCatalog.hasFeature(subscription.planCode(), feature);
    }

    @Transactional(readOnly = true)
    public void requireFeature(UUID userId, FeatureCode feature) {
        if (userId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (!hasFeature(userId, feature)) {
            throw new SubscriptionRequiredException(feature.value());
        }
    }

    private String entitlementPlan(SubscriptionSnapshot subscription) {
        if (subscription.planCode() == PlanCode.ADMIN) {
            return "ADMIN";
        }
        return subscription.premium() ? "PREMIUM" : "FREE";
    }
}
