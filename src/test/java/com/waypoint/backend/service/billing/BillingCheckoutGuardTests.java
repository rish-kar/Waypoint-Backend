package com.waypoint.backend.service.billing;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingCheckoutGuardTests {
    @Test
    void rejectsCheckoutBeforeCallingProviderWhenCoordinatorRejectsPaidSubscription() {
        LemonSqueezyClient lemonSqueezyClient = mock(LemonSqueezyClient.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        CheckoutSessionCoordinator checkoutSessionCoordinator = mock(CheckoutSessionCoordinator.class);
        LemonSqueezyProperties properties = new LemonSqueezyProperties(
                "test-api-key",
                "123",
                "111",
                "222",
                "test-webhook-secret",
                "https://api.lemonsqueezy.com/v1"
        );
        BillingService billingService = new BillingService(
                lemonSqueezyClient,
                properties,
                subscriptionService,
                planRepository,
                checkoutSessionCoordinator
        );
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        when(checkoutSessionCoordinator.reserve(user.getId(), CheckoutPlan.MONTHLY))
                .thenThrow(new InvalidRequestException("A paid subscription is already active for this account"));

        assertThatThrownBy(() -> billingService.createCheckout(user, CheckoutPlan.MONTHLY))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A paid subscription is already active for this account");
        verifyNoInteractions(lemonSqueezyClient);
    }
}
