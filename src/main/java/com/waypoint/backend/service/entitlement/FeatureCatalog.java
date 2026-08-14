package com.waypoint.backend.service.entitlement;

import com.waypoint.backend.model.entitlement.FeatureCode;
import com.waypoint.backend.model.plan.PlanCode;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FeatureCatalog {
    private static final List<FeatureCode> FREE_FEATURES = List.of(
            FeatureCode.INSTANT_TAB_SEARCH
    );
    private static final List<FeatureCode> PREMIUM_FEATURES = List.of(FeatureCode.values());

    public List<String> featuresFor(PlanCode planCode) {
        return featureCodesFor(planCode).stream()
                .map(FeatureCode::value)
                .toList();
    }

    public boolean hasFeature(PlanCode planCode, FeatureCode featureCode) {
        return featureCodesFor(planCode).contains(featureCode);
    }

    private List<FeatureCode> featureCodesFor(PlanCode planCode) {
        if (planCode == PlanCode.PREMIUM_MONTHLY
                || planCode == PlanCode.PREMIUM_ANNUAL
                || planCode == PlanCode.PREMIUM_SPECIAL) {
            return PREMIUM_FEATURES;
        }
        return FREE_FEATURES;
    }
}
