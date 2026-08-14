package com.waypoint.backend.service.subscription;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionAccessPolicyTests {
    private SubscriptionAccessPolicy policy;
    private Instant now;

    @BeforeEach
    void setUp() {
        policy = new SubscriptionAccessPolicy(new LemonSqueezyProperties(
                "test-api-key",
                "123",
                "111",
                "222",
                "test-webhook-secret",
                "https://api.lemonsqueezy.com/v1"
        ));
        now = Instant.parse("2026-08-14T16:00:00Z");
    }

    @Test
    void onTrialAccessUsesLemonSqueezyTrialEndDate() {
        SubscriptionEntity subscription = monthlyTrial();
        Instant trialEndsAt = now.plusSeconds(604800);
        subscription.setTrialEndsAt(trialEndsAt);
        subscription.setRenewsAt(now.plusSeconds(1209600));

        SubscriptionAccessDecision decision = policy.evaluate(subscription, now);

        assertThat(decision.premium()).isTrue();
        assertThat(decision.status()).isEqualTo(SubscriptionStatus.ON_TRIAL);
        assertThat(decision.validUntil()).isEqualTo(trialEndsAt);
    }

    @Test
    void onTrialAccessFailsClosedWithoutTrialEndDate() {
        SubscriptionEntity subscription = monthlyTrial();
        subscription.setRenewsAt(now.plusSeconds(604800));

        SubscriptionAccessDecision decision = policy.evaluate(subscription, now);

        assertThat(decision.premium()).isFalse();
        assertThat(decision.validUntil()).isNull();
    }

    @Test
    void onTrialAccessStopsAfterTrialEndDateEvenBeforeStatusWebhookArrives() {
        SubscriptionEntity subscription = monthlyTrial();
        subscription.setTrialEndsAt(now.minusSeconds(1));
        subscription.setRenewsAt(now.plusSeconds(604800));

        SubscriptionAccessDecision decision = policy.evaluate(subscription, now);

        assertThat(decision.premium()).isFalse();
        assertThat(decision.validUntil()).isNull();
    }

    private SubscriptionEntity monthlyTrial() {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setPlan(CheckoutPlan.MONTHLY.name());
        subscription.setExternalVariantId("111");
        subscription.setStatus(SubscriptionStatus.ON_TRIAL);
        return subscription;
    }
}
