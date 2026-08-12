package com.waypoint.backend.service.billing;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.plan.BillingInterval;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.service.subscription.SubscriptionAccessPolicy;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingServiceTests {
    private LemonSqueezyClient lemonSqueezyClient;
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionAccessPolicy subscriptionAccessPolicy;
    private PlanRepository planRepository;
    private BillingService billingService;

    @BeforeEach
    void setUp() {
        lemonSqueezyClient = mock(LemonSqueezyClient.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        subscriptionAccessPolicy = mock(SubscriptionAccessPolicy.class);
        planRepository = mock(PlanRepository.class);
        LemonSqueezyProperties properties = new LemonSqueezyProperties(
                "test-api-key",
                "123",
                "111",
                "222",
                "test-webhook-secret",
                "https://api.lemonsqueezy.com/v1"
        );
        billingService = new BillingService(
                lemonSqueezyClient,
                properties,
                subscriptionRepository,
                subscriptionAccessPolicy,
                planRepository
        );
    }

    @Test
    void createsMonthlyCheckoutWithMonthlyVariant() {
        UserEntity user = user();
        when(lemonSqueezyClient.createCheckout(user, CheckoutPlan.MONTHLY, "111"))
                .thenReturn("https://checkout.example/monthly");

        String result = billingService.createCheckout(user, CheckoutPlan.MONTHLY);

        assertThat(result).isEqualTo("https://checkout.example/monthly");
        verify(lemonSqueezyClient).createCheckout(user, CheckoutPlan.MONTHLY, "111");
    }

    @Test
    void createsAnnualCheckoutWithAnnualVariant() {
        UserEntity user = user();
        when(lemonSqueezyClient.createCheckout(user, CheckoutPlan.ANNUAL, "222"))
                .thenReturn("https://checkout.example/annual");

        String result = billingService.createCheckout(user, CheckoutPlan.ANNUAL);

        assertThat(result).isEqualTo("https://checkout.example/annual");
        verify(lemonSqueezyClient).createCheckout(user, CheckoutPlan.ANNUAL, "222");
    }

    @Test
    void rejectsMissingCheckoutPlan() {
        assertThatThrownBy(() -> billingService.createCheckout(user(), null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("plan is required");
    }

    @Test
    void returnsActivePremiumPlansFromDatabase() {
        PlanEntity monthly = plan(PlanCode.PREMIUM_MONTHLY, BillingInterval.MONTHLY, 499);
        PlanEntity annual = plan(PlanCode.PREMIUM_ANNUAL, BillingInterval.ANNUAL, 3999);
        when(planRepository.findByActiveTrueAndPremiumTrueOrderByPriceCentsAsc())
                .thenReturn(List.of(monthly, annual));

        List<PlanResponse> result = billingService.availablePlans();

        assertThat(result).extracting(PlanResponse::code)
                .containsExactly(PlanCode.PREMIUM_MONTHLY, PlanCode.PREMIUM_ANNUAL);
        assertThat(result).extracting(PlanResponse::priceCents)
                .containsExactly(499, 3999);
    }

    @Test
    void exposesExactMonthlyPlanCodeForActiveSubscription() {
        UserEntity user = user();
        SubscriptionEntity subscription = subscription(user, CheckoutPlan.MONTHLY);
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()))
                .thenReturn(List.of(subscription));
        when(subscriptionAccessPolicy.evaluate(eq(subscription), any(Instant.class)))
                .thenReturn(new SubscriptionAccessDecision(
                        true,
                        SubscriptionStatus.ACTIVE,
                        Instant.now().plusSeconds(3600)
                ));

        BillingStatusResponse result = billingService.billingStatus(user.getId());

        assertThat(result.plan()).isEqualTo("PREMIUM");
        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_MONTHLY);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void returnsFreePlanCodeWithoutSubscription() {
        UserEntity user = user();
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()))
                .thenReturn(List.of());

        BillingStatusResponse result = billingService.billingStatus(user.getId());

        assertThat(result.plan()).isEqualTo("FREE");
        assertThat(result.planCode()).isEqualTo(PlanCode.FREE);
        assertThat(result.status()).isEqualTo("INACTIVE");
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        return user;
    }

    private PlanEntity plan(PlanCode code, BillingInterval interval, int priceCents) {
        PlanEntity plan = new PlanEntity();
        plan.setCode(code);
        plan.setDisplayName(code.name());
        plan.setBillingInterval(interval);
        plan.setPriceCents(priceCents);
        plan.setCurrency("USD");
        plan.setPremium(true);
        plan.setActive(true);
        return plan;
    }

    private SubscriptionEntity subscription(UserEntity user, CheckoutPlan plan) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setUser(user);
        subscription.setPlan(plan.name());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setExternalSubscriptionId("sub_123");
        subscription.setUpdatedAt(Instant.now());
        return subscription;
    }
}
