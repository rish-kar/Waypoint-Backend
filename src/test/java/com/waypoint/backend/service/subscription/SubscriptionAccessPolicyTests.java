package com.waypoint.backend.service.subscription;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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
        SubscriptionEntity subscription = monthly(SubscriptionStatus.ON_TRIAL);
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
        SubscriptionEntity subscription = monthly(SubscriptionStatus.ON_TRIAL);
        subscription.setRenewsAt(now.plusSeconds(604800));

        SubscriptionAccessDecision decision = policy.evaluate(subscription, now);

        assertThat(decision.premium()).isFalse();
        assertThat(decision.validUntil()).isNull();
    }

    @Test
    void onTrialAccessStopsAfterTrialEndDateEvenBeforeStatusWebhookArrives() {
        SubscriptionEntity subscription = monthly(SubscriptionStatus.ON_TRIAL);
        subscription.setTrialEndsAt(now.minusSeconds(1));
        subscription.setRenewsAt(now.plusSeconds(604800));

        SubscriptionAccessDecision decision = policy.evaluate(subscription, now);

        assertThat(decision.premium()).isFalse();
        assertThat(decision.validUntil()).isNull();
    }

    @Test
    void paidStatusesRequireAFutureRenewalDate() {
        for (SubscriptionStatus status : List.of(
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.PAUSED,
                SubscriptionStatus.PAST_DUE
        )) {
            SubscriptionEntity missingRenewal = monthly(status);
            SubscriptionAccessDecision missingDecision = policy.evaluate(missingRenewal, now);
            assertThat(missingDecision.premium()).as("missing renewal for %s", status).isFalse();
            assertThat(missingDecision.validUntil()).isNull();

            SubscriptionEntity expiredRenewal = monthly(status);
            expiredRenewal.setRenewsAt(now.minusSeconds(1));
            SubscriptionAccessDecision expiredDecision = policy.evaluate(expiredRenewal, now);
            assertThat(expiredDecision.premium()).as("expired renewal for %s", status).isFalse();
            assertThat(expiredDecision.validUntil()).isNull();
        }
    }

    @Test
    void paidStatusesRetainPremiumAccessUntilFutureRenewalDate() {
        Instant nextBillingAttempt = now.plusSeconds(3600);
        for (SubscriptionStatus status : List.of(
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.PAUSED,
                SubscriptionStatus.PAST_DUE
        )) {
            SubscriptionEntity subscription = monthly(status);
            subscription.setRenewsAt(nextBillingAttempt);

            SubscriptionAccessDecision decision = policy.evaluate(subscription, now);

            assertThat(decision.premium()).as("status %s", status).isTrue();
            assertThat(decision.status()).isEqualTo(status);
            assertThat(decision.validUntil()).isEqualTo(nextBillingAttempt);
        }
    }

    @Test
    void refundedAndExpiredSubscriptionsRemainNonPremium() {
        for (SubscriptionStatus status : List.of(SubscriptionStatus.REFUNDED, SubscriptionStatus.EXPIRED)) {
            SubscriptionAccessDecision decision = policy.evaluate(monthly(status), now);
            assertThat(decision.premium()).as("status %s", status).isFalse();
        }
    }

    private SubscriptionEntity monthly(SubscriptionStatus status) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setPlan(CheckoutPlan.MONTHLY.name());
        subscription.setExternalVariantId("111");
        subscription.setStatus(status);
        return subscription;
    }
}
