package com.waypoint.backend.service.billing;

import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.billing.BillingCheckoutSessionEntity;
import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.plan.BillingInterval;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.billing.BillingCheckoutSessionRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.client.lemonsqueezy.LemonSqueezyClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingServiceTests {
    private LemonSqueezyClient lemonSqueezyClient;
    private SubscriptionService subscriptionService;
    private PlanRepository planRepository;
    private BillingCheckoutSessionRepository checkoutSessionRepository;
    private UserRepository userRepository;
    private BillingService billingService;

    @BeforeEach
    void setUp() {
        lemonSqueezyClient = mock(LemonSqueezyClient.class);
        subscriptionService = mock(SubscriptionService.class);
        planRepository = mock(PlanRepository.class);
        checkoutSessionRepository = mock(BillingCheckoutSessionRepository.class);
        userRepository = mock(UserRepository.class);
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
                checkoutSessionRepository,
                userRepository
        );
    }

    @Test
    void createsMonthlyCheckoutWithMonthlyVariant() {
        UserEntity user = user();
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(lemonSqueezyClient.createCheckout(user, CheckoutPlan.MONTHLY, "111"))
                .thenReturn("https://checkout.example/monthly");

        String result = billingService.createCheckout(user, CheckoutPlan.MONTHLY);

        assertThat(result).isEqualTo("https://checkout.example/monthly");
        verify(lemonSqueezyClient).createCheckout(user, CheckoutPlan.MONTHLY, "111");
        verify(checkoutSessionRepository).save(any(BillingCheckoutSessionEntity.class));
    }

    @Test
    void createsAnnualCheckoutWithAnnualVariant() {
        UserEntity user = user();
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(lemonSqueezyClient.createCheckout(user, CheckoutPlan.ANNUAL, "222"))
                .thenReturn("https://checkout.example/annual");

        String result = billingService.createCheckout(user, CheckoutPlan.ANNUAL);

        assertThat(result).isEqualTo("https://checkout.example/annual");
        verify(lemonSqueezyClient).createCheckout(user, CheckoutPlan.ANNUAL, "222");
    }

    @Test
    void reusesPendingCheckoutInsteadOfCreatingDuplicateProviderCheckout() {
        UserEntity user = user();
        AtomicReference<BillingCheckoutSessionEntity> storedSession = new AtomicReference<>();
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(checkoutSessionRepository.findById(user.getId()))
                .thenAnswer(invocation -> Optional.ofNullable(storedSession.get()));
        when(checkoutSessionRepository.save(any(BillingCheckoutSessionEntity.class)))
                .thenAnswer(invocation -> {
                    BillingCheckoutSessionEntity session = invocation.getArgument(0);
                    storedSession.set(session);
                    return session;
                });
        when(lemonSqueezyClient.createCheckout(user, CheckoutPlan.MONTHLY, "111"))
                .thenReturn("https://checkout.example/monthly");

        String first = billingService.createCheckout(user, CheckoutPlan.MONTHLY);
        String second = billingService.createCheckout(user, CheckoutPlan.MONTHLY);

        assertThat(first).isEqualTo("https://checkout.example/monthly");
        assertThat(second).isEqualTo(first);
        verify(lemonSqueezyClient, times(1)).createCheckout(user, CheckoutPlan.MONTHLY, "111");
    }

    @Test
    void rejectsDifferentPlanWhileCheckoutIsStillPending() {
        UserEntity user = user();
        BillingCheckoutSessionEntity session = new BillingCheckoutSessionEntity();
        session.setUserId(user.getId());
        session.setPlan(CheckoutPlan.MONTHLY);
        session.setCheckoutUrl("https://checkout.example/monthly");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(checkoutSessionRepository.findById(user.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> billingService.createCheckout(user, CheckoutPlan.ANNUAL))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A checkout for another billing plan is already pending for this account");
    }

    @Test
    void rejectsMissingCheckoutPlan() {
        assertThatThrownBy(() -> billingService.createCheckout(user(), null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("plan is required");
    }

    @Test
    void returnsOnlyPaidPremiumPlansFromDatabase() {
        PlanEntity monthly = plan(PlanCode.PREMIUM_MONTHLY, BillingInterval.MONTHLY, 499);
        PlanEntity annual = plan(PlanCode.PREMIUM_ANNUAL, BillingInterval.ANNUAL, 3999);
        when(planRepository.findByActiveTrueAndPremiumTrueAndBillingIntervalNotOrderByPriceCentsAsc(BillingInterval.NONE))
                .thenReturn(List.of(monthly, annual));

        List<PlanResponse> result = billingService.availablePlans();

        assertThat(result).extracting(PlanResponse::code)
                .containsExactly(PlanCode.PREMIUM_MONTHLY, PlanCode.PREMIUM_ANNUAL);
        verify(planRepository)
                .findByActiveTrueAndPremiumTrueAndBillingIntervalNotOrderByPriceCentsAsc(BillingInterval.NONE);
    }

    @Test
    void exposesExactMonthlyPlanCodeForActiveSubscription() {
        UserEntity user = user();
        when(subscriptionService.currentBilling(user.getId())).thenReturn(snapshot(
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
        assertThat(result.trialEndsAt()).isNull();
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
        when(subscriptionService.currentBilling(user.getId())).thenReturn(snapshot);

        BillingStatusResponse result = billingService.billingStatus(user.getId());

        assertThat(result.plan()).isEqualTo("PREMIUM");
        assertThat(result.planCode()).isEqualTo(PlanCode.PREMIUM_MONTHLY);
        assertThat(result.status()).isEqualTo("ON_TRIAL");
        assertThat(result.externalSubscriptionId()).isEqualTo("sub_trial");
        assertThat(result.trialEndsAt()).isEqualTo(snapshot.trialEndsAt());
    }

    @Test
    void returnsFreePlanCodeWithoutPaidPremiumAccess() {
        UserEntity user = user();
        when(subscriptionService.currentBilling(user.getId())).thenReturn(snapshot(
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
