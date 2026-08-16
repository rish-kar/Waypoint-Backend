package com.waypoint.backend.service.billing;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.billing.BillingCheckoutSessionRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingCheckoutGuardTests {
    @Test
    void rejectsCheckoutBeforeCallingProviderWhenPaidSubscriptionExists() {
        LemonSqueezyClient lemonSqueezyClient = mock(LemonSqueezyClient.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        BillingCheckoutSessionRepository checkoutSessionRepository = mock(BillingCheckoutSessionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
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
                checkoutSessionRepository,
                userRepository
        );
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(subscriptionService.hasCheckoutBlockingSubscription(user.getId())).thenReturn(true);

        assertThatThrownBy(() -> billingService.createCheckout(user, CheckoutPlan.MONTHLY))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A paid subscription is already active for this account");
        verifyNoInteractions(lemonSqueezyClient);
    }
}
