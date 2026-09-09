package com.waypoint.backend.service.billing;

import com.waypoint.backend.model.ai.AiUsageResponse;
import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.subscription.ProviderSubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.service.ai.AiUsageService;
import com.waypoint.backend.service.subscription.SubscriptionReconciliationService;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezySubscriptionClient;
import com.waypoint.backend.utilities.exception.ApiException;
import com.waypoint.backend.utilities.exception.ExternalServiceException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class TrialConversionService {
    private final AiUsageService aiUsageService;
    private final SubscriptionService subscriptionService;
    private final LemonSqueezySubscriptionClient lemonSqueezySubscriptionClient;
    private final SubscriptionReconciliationService subscriptionReconciliationService;

    public TrialConversionService(
            AiUsageService aiUsageService,
            SubscriptionService subscriptionService,
            LemonSqueezySubscriptionClient lemonSqueezySubscriptionClient,
            SubscriptionReconciliationService subscriptionReconciliationService
    ) {
        this.aiUsageService = aiUsageService;
        this.subscriptionService = subscriptionService;
        this.lemonSqueezySubscriptionClient = lemonSqueezySubscriptionClient;
        this.subscriptionReconciliationService = subscriptionReconciliationService;
    }

    public BillingStatusResponse skipTrial(UUID userId) {
        SubscriptionSnapshot current = subscriptionService.currentBilling(userId);
        if (current.status() == SubscriptionStatus.ACTIVE && current.premium()) {
            return toResponse(current);
        }
        if (current.status() != SubscriptionStatus.ON_TRIAL || !current.premium()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TRIAL_SKIP_NOT_AVAILABLE",
                    "There is no active Lemon Squeezy trial to skip."
            );
        }
        if (!StringUtils.hasText(current.externalSubscriptionId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TRIAL_SKIP_NOT_AVAILABLE",
                    "The active trial is not linked to Lemon Squeezy."
            );
        }

        AiUsageResponse usage = aiUsageService.current(userId);
        if (!usage.trialLimited() || usage.trialRemaining() > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "AI_TRIAL_NOT_EXHAUSTED",
                    "Cloud AI trial requests are still available."
            );
        }

        ProviderSubscriptionSnapshot providerSnapshot =
                lemonSqueezySubscriptionClient.skipTrial(current.externalSubscriptionId());
        if (providerSnapshot.providerUpdatedAt() == null
                || providerSnapshot.trialEndsAt() != null
                || SubscriptionStatus.fromExternal(providerSnapshot.status()) == SubscriptionStatus.ON_TRIAL) {
            throw new ExternalServiceException("Lemon Squeezy did not end the trial");
        }

        subscriptionReconciliationService.reconcile(providerSnapshot);

        SubscriptionSnapshot synchronizedSubscription = subscriptionService.currentBilling(userId);
        if (synchronizedSubscription.status() == SubscriptionStatus.ON_TRIAL) {
            throw new ExternalServiceException("The Lemon Squeezy trial ended but Waypoint did not synchronize the new state");
        }
        return toResponse(synchronizedSubscription);
    }

    private BillingStatusResponse toResponse(SubscriptionSnapshot subscription) {
        return new BillingStatusResponse(
                subscription.premium() ? "PREMIUM" : "FREE",
                subscription.planCode(),
                subscription.status().name(),
                subscription.externalSubscriptionId(),
                subscription.trialEndsAt(),
                subscription.renewsAt(),
                subscription.endsAt()
        );
    }
}
