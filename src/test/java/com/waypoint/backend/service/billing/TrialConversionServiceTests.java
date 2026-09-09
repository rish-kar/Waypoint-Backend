package com.waypoint.backend.service.billing;

import com.waypoint.backend.model.ai.AiUsageResponse;
import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.ProviderSubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.service.ai.AiUsageService;
import com.waypoint.backend.service.subscription.SubscriptionReconciliationService;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezySubscriptionClient;
import com.waypoint.backend.utilities.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrialConversionServiceTests {
    private AiUsageService aiUsageService;
    private SubscriptionService subscriptionService;
    private LemonSqueezySubscriptionClient lemonSqueezySubscriptionClient;
    private SubscriptionReconciliationService subscriptionReconciliationService;
    private TrialConversionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        aiUsageService = mock(AiUsageService.class);
        subscriptionService = mock(SubscriptionService.class);
        lemonSqueezySubscriptionClient = mock(LemonSqueezySubscriptionClient.class);
        subscriptionReconciliationService = mock(SubscriptionReconciliationService.class);
        service = new TrialConversionService(
                aiUsageService,
                subscriptionService,
                lemonSqueezySubscriptionClient,
                subscriptionReconciliationService
        );
        userId = UUID.randomUUID();
    }

    @Test
    void exhaustedTrialIsEndedAtProviderThenReconciledFromProviderSnapshot() {
        Instant now = Instant.parse("2026-09-09T16:30:00Z");
        Instant providerUpdatedAt = now.plusSeconds(2);
        Instant renewal = now.plus(30, ChronoUnit.DAYS);
        SubscriptionSnapshot trial = snapshot(
                SubscriptionStatus.ON_TRIAL,
                true,
                "2514315",
                now.plus(7, ChronoUnit.DAYS),
                now.plus(7, ChronoUnit.DAYS),
                now
        );
        SubscriptionSnapshot active = snapshot(
                SubscriptionStatus.ACTIVE,
                true,
                "2514315",
                null,
                renewal,
                providerUpdatedAt
        );
        ProviderSubscriptionSnapshot provider = new ProviderSubscriptionSnapshot(
                "2514315",
                "user@example.com",
                "100",
                "200",
                "2018836",
                "active",
                null,
                renewal,
                null,
                providerUpdatedAt
        );

        when(subscriptionService.currentBilling(userId)).thenReturn(trial, active);
        when(aiUsageService.current(userId)).thenReturn(new AiUsageResponse(false, true, 20, 20, 0, "ON_TRIAL"));
        when(lemonSqueezySubscriptionClient.skipTrial("2514315")).thenReturn(provider);
        when(subscriptionReconciliationService.reconcile(provider))
                .thenReturn(SubscriptionReconciliationService.Result.APPLIED);

        BillingStatusResponse result = service.skipTrial(userId);

        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.trialEndsAt()).isNull();
        assertThat(result.renewsAt()).isEqualTo(renewal);
        verify(lemonSqueezySubscriptionClient).skipTrial("2514315");
        verify(subscriptionReconciliationService).reconcile(provider);
    }

    @Test
    void trialCannotBeSkippedBeforeCloudQuotaIsExhausted() {
        Instant now = Instant.parse("2026-09-09T16:30:00Z");
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(
                SubscriptionStatus.ON_TRIAL,
                true,
                "2514315",
                now.plus(7, ChronoUnit.DAYS),
                now.plus(7, ChronoUnit.DAYS),
                now
        ));
        when(aiUsageService.current(userId)).thenReturn(new AiUsageResponse(true, true, 20, 12, 8, "ON_TRIAL"));

        assertThatThrownBy(() -> service.skipTrial(userId)).isInstanceOf(ApiException.class);

        verifyNoInteractions(lemonSqueezySubscriptionClient, subscriptionReconciliationService);
    }

    @Test
    void alreadyActivePremiumIsIdempotentAndDoesNotTouchProvider() {
        Instant now = Instant.parse("2026-09-09T16:30:00Z");
        when(subscriptionService.currentBilling(userId)).thenReturn(snapshot(
                SubscriptionStatus.ACTIVE,
                true,
                "2514315",
                null,
                now.plus(30, ChronoUnit.DAYS),
                now
        ));

        BillingStatusResponse result = service.skipTrial(userId);

        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(aiUsageService, never()).current(userId);
        verifyNoInteractions(lemonSqueezySubscriptionClient, subscriptionReconciliationService);
    }

    private SubscriptionSnapshot snapshot(
            SubscriptionStatus status,
            boolean premium,
            String externalSubscriptionId,
            Instant trialEndsAt,
            Instant renewsAt,
            Instant checkedAt
    ) {
        return new SubscriptionSnapshot(
                PlanCode.PREMIUM_MONTHLY,
                status,
                premium,
                externalSubscriptionId,
                trialEndsAt,
                renewsAt,
                null,
                premium ? (trialEndsAt != null ? trialEndsAt : renewsAt) : null,
                checkedAt
        );
    }
}
