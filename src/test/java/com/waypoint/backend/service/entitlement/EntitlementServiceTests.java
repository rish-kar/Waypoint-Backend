package com.waypoint.backend.service.entitlement;

import com.waypoint.backend.model.entitlement.FeatureCode;
import com.waypoint.backend.model.entitlement.FeatureEntitlementResponse;
import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntitlementServiceTests {
    private SubscriptionService subscriptionService;
    private EntitlementService entitlementService;
    private UUID userId;
    private Instant now;

    @BeforeEach
    void setUp() {
        subscriptionService = mock(SubscriptionService.class);
        entitlementService = new EntitlementService(subscriptionService, new FeatureCatalog());
        userId = UUID.randomUUID();
        now = Instant.parse("2026-08-12T08:00:00Z");
    }

    @Test
    void freePlanContainsOnlyInstantTabSearch() {
        when(subscriptionService.current(userId)).thenReturn(freeSnapshot());

        EntitlementResponse result = entitlementService.currentEntitlement(userId, true);

        assertThat(result.plan()).isEqualTo("FREE");
        assertThat(result.premium()).isFalse();
        assertThat(result.features()).containsExactly("instant-tab-search");
        assertThat(result.checkedAt()).isEqualTo(now);
    }

    @Test
    void premiumPlanContainsAllFeaturesInStableOrder() {
        when(subscriptionService.current(userId)).thenReturn(premiumSnapshot());

        EntitlementResponse result = entitlementService.currentEntitlement(userId, true);

        assertThat(result.plan()).isEqualTo("PREMIUM");
        assertThat(result.premium()).isTrue();
        assertThat(result.features()).containsExactly(
                "instant-tab-search",
                "duplicate-tabs",
                "saved-workspaces",
                "tab-tasks",
                "snooze-tabs",
                "smart-tab-groups",
                "calendar-slack-integrations",
                "session-timeline",
                "ai-summary"
        );
    }

    @Test
    void featureAccessReturnsFalseForPremiumOnlyFeatureOnFreePlan() {
        when(subscriptionService.current(userId)).thenReturn(freeSnapshot());

        FeatureEntitlementResponse result = entitlementService.featureAccess(userId, "ai-summary");

        assertThat(result.feature()).isEqualTo("ai-summary");
        assertThat(result.allowed()).isFalse();
        assertThat(result.plan()).isEqualTo("FREE");
    }

    @Test
    void featureAccessReturnsTrueForPremiumPlan() {
        when(subscriptionService.current(userId)).thenReturn(premiumSnapshot());

        assertThat(entitlementService.hasFeature(userId, FeatureCode.AI_SUMMARY)).isTrue();
        assertThat(entitlementService.featureAccess(userId, "AI-SUMMARY").allowed()).isTrue();
    }

    @Test
    void rejectsUnknownFeature() {
        assertThatThrownBy(() -> entitlementService.featureAccess(userId, "does-not-exist"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown feature");
    }

    private SubscriptionSnapshot freeSnapshot() {
        return new SubscriptionSnapshot(
                PlanCode.FREE,
                SubscriptionStatus.INACTIVE,
                false,
                null,
                null,
                null,
                null,
                now
        );
    }

    private SubscriptionSnapshot premiumSnapshot() {
        Instant validUntil = now.plusSeconds(3600);
        return new SubscriptionSnapshot(
                PlanCode.PREMIUM_MONTHLY,
                SubscriptionStatus.ACTIVE,
                true,
                "sub-1",
                validUntil,
                null,
                validUntil,
                now
        );
    }
}
