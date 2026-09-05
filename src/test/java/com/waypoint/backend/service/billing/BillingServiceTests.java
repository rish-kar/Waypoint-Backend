package com.waypoint.backend.service.billing;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.billing.ProviderPriceCatalog;
import com.waypoint.backend.model.plan.BillingInterval;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingServiceTests {
    private LemonSqueezyClient lemonSqueezyClient;
    private SubscriptionService subscriptionService;
    private PlanRepository planRepository;
    private CheckoutSessionCoordinator checkoutSessionCoordinator;
    private BillingService billingService;

    @BeforeEach
    void setUp() {
        lemonSqueezyClient = mock(LemonSqueezyClient.class);
        subscriptionService = mock(SubscriptionService.class);
        planRepository = mock(PlanRepository.class);
        checkoutSessionCoordinator = mock(CheckoutSessionCoordinator.class);
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
                subscriptionService,
                planRepository,
                checkoutSessionCoordinator
        );
    }

    @Test
    void createsMonthlyCheckoutWithMonthlyVariantOutsideReservationTransaction() {
        UserEntity user = user();
        UUID intentId = UUID.randomUUID();
        when(checkoutSessionCoordinator.reserve(user.getId(), CheckoutPlan.MONTHLY))
                .thenReturn(new CheckoutSessionCoordinator.Reservation(user, intentId, null, true));
        when(lemonSqueezyClient.findCheckoutByIntent("111", intentId)).thenReturn(Optional.empty());
        when(lemonSqueezyClient.createCheckout(user, CheckoutPlan.MONTHLY, "111", intentId))
                .thenReturn("https://checkout.example/monthly");

        String result = billingService.createCheckout(user, CheckoutPlan.MONTHLY);

        assertThat(result).isEqualTo("https://checkout.example/monthly");
        verify(lemonSqueezyClient).createCheckout(user, CheckoutPlan.MONTHLY, "111", intentId);
        verify(checkoutSessionCoordinator).complete(user.getId(), intentId, result);
    }

    @Test
    void createsAnnualCheckoutWithAnnualVariant() {
        UserEntity user = user();
        UUID intentId = UUID.randomUUID();
        when(checkoutSessionCoordinator.reserve(user.getId(), CheckoutPlan.ANNUAL))
                .thenReturn(new CheckoutSessionCoordinator.Reservation(user, intentId, null, true));
        when(lemonSqueezyClient.findCheckoutByIntent("222", intentId)).thenReturn(Optional.empty());
        when(lemonSqueezyClient.createCheckout(user, CheckoutPlan.ANNUAL, "222", intentId))
                .thenReturn("https://checkout.example/annual");

        String result = billingService.createCheckout(user, CheckoutPlan.ANNUAL);

        assertThat(result).isEqualTo("https://checkout.example/annual");
        verify(checkoutSessionCoordinator).complete(user.getId(), intentId, result);
    }

    @Test
    void reusesCompletedCheckoutWithoutCallingProvider() {
        UserEntity user = user();
        UUID intentId = UUID.randomUUID();
        when(checkoutSessionCoordinator.reserve(user.getId(), CheckoutPlan.MONTHLY))
                .thenReturn(new CheckoutSessionCoordinator.Reservation(
                        user,
                        intentId,
                        "https://checkout.example/monthly",
                        false
                ));

        String result = billingService.createCheckout(user, CheckoutPlan.MONTHLY);

        assertThat(result).isEqualTo("https://checkout.example/monthly");
        verifyNoInteractions(lemonSqueezyClient);
    }

    @Test
    void recoversProviderCheckoutAfterLocalCompletionFailure() {
        UserEntity user = user();
        UUID intentId = UUID.randomUUID();
        when(checkoutSessionCoordinator.reserve(user.getId(), CheckoutPlan.MONTHLY))
                .thenReturn(new CheckoutSessionCoordinator.Reservation(user, intentId, null, true));
        when(lemonSqueezyClient.findCheckoutByIntent("111", intentId))
                .thenReturn(Optional.of("https://checkout.example/recovered"));

        String result = billingService.createCheckout(user, CheckoutPlan.MONTHLY);

        assertThat(result).isEqualTo("https://checkout.example/recovered");
        verify(lemonSqueezyClient, never()).createCheckout(user, CheckoutPlan.MONTHLY, "111", intentId);
        verify(checkoutSessionCoordinator).complete(user.getId(), intentId, result);
    }

    @Test
    void rejectsConcurrentProviderCreationWhileLeaseIsActive() {
        UserEntity user = user();
        UUID intentId = UUID.randomUUID();
        when(checkoutSessionCoordinator.reserve(user.getId(), CheckoutPlan.MONTHLY))
                .thenReturn(new CheckoutSessionCoordinator.Reservation(user, intentId, null, false));

        assertThatThrownBy(() -> billingService.createCheckout(user, CheckoutPlan.MONTHLY))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Checkout is already being prepared; retry shortly");
        verifyNoInteractions(lemonSqueezyClient);
    }

    @Test
    void rejectsMissingCheckoutPlan() {
        assertThatThrownBy(() -> billingService.createCheckout(user(), null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("plan is required");
    }

    @Test
    void returnsPaidPlansUsingProviderPricesAndCachesCatalog() {
        PlanEntity monthly = plan(PlanCode.PREMIUM_MONTHLY, BillingInterval.MONTHLY, 499);
        PlanEntity annual = plan(PlanCode.PREMIUM_ANNUAL, BillingInterval.ANNUAL, 3999);
        when(planRepository.findByActiveTrueAndPremiumTrueAndBillingIntervalNotOrderByPriceCentsAsc(BillingInterval.NONE))
                .thenReturn(List.of(monthly, annual));
        when(lemonSqueezyClient.fetchPriceCatalog("111", "222"))
                .thenReturn(new ProviderPriceCatalog(599, 4999, "GBP"));

        List<PlanResponse> first = billingService.availablePlans();
        List<PlanResponse> second = billingService.availablePlans();

        assertThat(first).extracting(PlanResponse::priceCents).containsExactly(599, 4999);
        assertThat(second).extracting(PlanResponse::currency).containsOnly("GBP");
        verify(lemonSqueezyClient, times(1)).fetchPriceCatalog("111", "222");
    }

    @Test
    void exposesExactMonthlyPlanCodeForActiveSubscription() {
        UserEntity user = user();
        when(subscriptionService.current(user.getId())).thenReturn(snapshot(
                PlanCode.PREMIUM_MONTHLY,
                SubscriptionStatus.ACTIVE,
                true,
                "sub_123"
        ));

        BillingStatusResponse result = billingService.billingStatus(user.getId());

        assertThat(result.plan()).isEqualTo("PREMIUM");
        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_MONTHLY);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.externalSubscriptionId()).isEqualTo("sub_123");
    }

    @Test
    void exposesSpecialPremiumFromEffectiveSubscription() {
        UserEntity user = user();
        when(subscriptionService.current(user.getId())).thenReturn(snapshot(
                PlanCode.PREMIUM_SPECIAL,
                SubscriptionStatus.PREMIUM_SPECIAL,
                true,
                null
        ));

        BillingStatusResponse result = billingService.billingStatus(user.getId());

        assertThat(result.plan()).isEqualTo("PREMIUM");
        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_SPECIAL);
        assertThat(result.status()).isEqualTo("PREMIUM_SPECIAL");
    }

    @Test
    void exposesOnTrialBillingSubscriptionAndLemonSqueezyTrialEndDate() {
        UserEntity user = user();
        SubscriptionSnapshot snapshot = snapshot(
                PlanCode.PREMIUM_MONTHLY,
                SubscriptionStatus.ON_TRIAL,
                true,
                "sub_trial"
        );
        when(subscriptionService.current(user.getId())).thenReturn(snapshot);

        BillingStatusResponse result = billingService.billingStatus(user.getId());

        assertThat(result.plan()).isEqualTo("PREMIUM");
        assertThat(result.status()).isEqualTo("ON_TRIAL");
        assertThat(result.trialEndsAt()).isEqualTo(snapshot.trialEndsAt());
    }

    @Test
    void returnsFreePlanCodeWithoutPaidPremiumAccess() {
        UserEntity user = user();
        when(subscriptionService.current(user.getId())).thenReturn(snapshot(
                PlanCode.FREE,
                SubscriptionStatus.INACTIVE,
                false,
                null
        ));

        BillingStatusResponse result = billingService.billingStatus(user.getId());

        assertThat(result.plan()).isEqualTo("FREE");
        assertThat(result.planCode()).isEqualTo(PlanCode.FREE);
        assertThat(result.status()).isEqualTo("INACTIVE");
        assertThat(result.externalSubscriptionId()).isNull();
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

    private SubscriptionSnapshot snapshot(
            PlanCode planCode,
            SubscriptionStatus status,
            boolean premium,
            String externalSubscriptionId
    ) {
        Instant now = Instant.now();
        Instant trialEndsAt = status == SubscriptionStatus.ON_TRIAL ? now.plusSeconds(604800) : null;
        Instant renewsAt = premium ? now.plusSeconds(3600) : null;
        Instant validUntil = trialEndsAt != null ? trialEndsAt : renewsAt;
        return new SubscriptionSnapshot(
                planCode,
                status,
                premium,
                externalSubscriptionId,
                trialEndsAt,
                renewsAt,
                null,
                validUntil,
                now
        );
    }
}
