package com.waypoint.backend.plan;

import com.waypoint.backend.subscription.CheckoutPlan;
import com.waypoint.backend.subscription.SubscriptionAccessDecision;
import com.waypoint.backend.subscription.SubscriptionAccessPolicy;
import com.waypoint.backend.subscription.SubscriptionEntity;
import com.waypoint.backend.subscription.SubscriptionRepository;
import com.waypoint.backend.subscription.SubscriptionStatus;
import com.waypoint.backend.user.UserEntity;
import com.waypoint.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanServiceTests {
    private PlanRepository planRepository;
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionAccessPolicy subscriptionAccessPolicy;
    private UserRepository userRepository;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        subscriptionAccessPolicy = mock(SubscriptionAccessPolicy.class);
        userRepository = mock(UserRepository.class);
        planService = new PlanService(
                planRepository,
                subscriptionRepository,
                subscriptionAccessPolicy,
                userRepository
        );
    }

    @Test
    void assignsFreePlanWithoutActiveSubscription() {
        UserEntity user = user();
        PlanEntity free = plan(PlanCode.FREE);
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())).thenReturn(List.of());
        when(planRepository.findById(PlanCode.FREE)).thenReturn(Optional.of(free));

        PlanEntity result = planService.synchronizeUserPlan(user);

        assertThat(result).isSameAs(free);
        assertThat(user.getPlan().getCode()).isEqualTo(PlanCode.FREE);
        verify(userRepository).save(user);
    }

    @Test
    void assignsMonthlyPremiumPlanForActiveMonthlySubscription() {
        UserEntity user = user();
        SubscriptionEntity subscription = subscription(user, CheckoutPlan.MONTHLY);
        PlanEntity monthly = plan(PlanCode.PREMIUM_MONTHLY);
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()))
                .thenReturn(List.of(subscription));
        when(subscriptionAccessPolicy.evaluate(eq(subscription), any(Instant.class)))
                .thenReturn(new SubscriptionAccessDecision(true, SubscriptionStatus.ACTIVE, Instant.now().plusSeconds(3600)));
        when(planRepository.findById(PlanCode.PREMIUM_MONTHLY)).thenReturn(Optional.of(monthly));

        PlanEntity result = planService.synchronizeUserPlan(user);

        assertThat(result).isSameAs(monthly);
        assertThat(user.getPlan().getCode()).isEqualTo(PlanCode.PREMIUM_MONTHLY);
        verify(userRepository).save(user);
    }

    @Test
    void returnsUserToFreePlanAfterPremiumAccessEnds() {
        UserEntity user = user();
        user.setPlan(plan(PlanCode.PREMIUM_ANNUAL));
        SubscriptionEntity subscription = subscription(user, CheckoutPlan.ANNUAL);
        PlanEntity free = plan(PlanCode.FREE);
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()))
                .thenReturn(List.of(subscription));
        when(subscriptionAccessPolicy.evaluate(eq(subscription), any(Instant.class)))
                .thenReturn(new SubscriptionAccessDecision(false, SubscriptionStatus.REFUNDED, null));
        when(planRepository.findById(PlanCode.FREE)).thenReturn(Optional.of(free));

        PlanEntity result = planService.synchronizeUserPlan(user);

        assertThat(result).isSameAs(free);
        assertThat(user.getPlan().getCode()).isEqualTo(PlanCode.FREE);
        verify(userRepository).save(user);
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        return user;
    }

    private SubscriptionEntity subscription(UserEntity user, CheckoutPlan checkoutPlan) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setUser(user);
        subscription.setPlan(checkoutPlan.name());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setUpdatedAt(Instant.now());
        return subscription;
    }

    private PlanEntity plan(PlanCode code) {
        PlanEntity plan = new PlanEntity();
        plan.setCode(code);
        return plan;
    }
}
