package com.waypoint.backend.service.plan;

import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.subscription.SubscriptionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanServiceTests {
    private PlanRepository planRepository;
    private SubscriptionService subscriptionService;
    private UserRepository userRepository;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        subscriptionService = mock(SubscriptionService.class);
        userRepository = mock(UserRepository.class);
        planService = new PlanService(
                planRepository,
                subscriptionService,
                userRepository
        );
    }

    @Test
    void assignsFreePlanWithoutActiveSubscription() {
        UserEntity user = user();
        PlanEntity free = plan(PlanCode.FREE);
        when(subscriptionService.current(user.getId())).thenReturn(snapshot(PlanCode.FREE, false));
        when(planRepository.findById(PlanCode.FREE)).thenReturn(Optional.of(free));

        PlanEntity result = planService.synchronizeUserPlan(user);

        assertThat(result).isSameAs(free);
        assertThat(user.getPlan().getCode()).isEqualTo(PlanCode.FREE);
        verify(userRepository).save(user);
    }

    @Test
    void assignsMonthlyPremiumPlanForActiveMonthlySubscription() {
        UserEntity user = user();
        PlanEntity monthly = plan(PlanCode.PREMIUM_MONTHLY);
        when(subscriptionService.current(user.getId()))
                .thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, true));
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
        PlanEntity free = plan(PlanCode.FREE);
        when(subscriptionService.current(user.getId())).thenReturn(snapshot(PlanCode.FREE, false));
        when(planRepository.findById(PlanCode.FREE)).thenReturn(Optional.of(free));

        PlanEntity result = planService.synchronizeUserPlan(user);

        assertThat(result).isSameAs(free);
        assertThat(user.getPlan().getCode()).isEqualTo(PlanCode.FREE);
        verify(userRepository).save(user);
    }

    @Test
    void doesNotWriteWhenPlanIsAlreadySynchronized() {
        UserEntity user = user();
        PlanEntity monthly = plan(PlanCode.PREMIUM_MONTHLY);
        user.setPlan(monthly);
        when(subscriptionService.current(user.getId()))
                .thenReturn(snapshot(PlanCode.PREMIUM_MONTHLY, true));

        PlanEntity result = planService.synchronizeUserPlan(user);

        assertThat(result).isSameAs(monthly);
        org.mockito.Mockito.verifyNoInteractions(planRepository);
        org.mockito.Mockito.verifyNoInteractions(userRepository);
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        return user;
    }

    private SubscriptionSnapshot snapshot(PlanCode planCode, boolean premium) {
        Instant now = Instant.now();
        return new SubscriptionSnapshot(
                planCode,
                premium ? SubscriptionStatus.ACTIVE : SubscriptionStatus.INACTIVE,
                premium,
                premium ? "sub-1" : null,
                premium ? now.plusSeconds(3600) : null,
                null,
                premium ? now.plusSeconds(3600) : null,
                now
        );
    }

    private PlanEntity plan(PlanCode code) {
        PlanEntity plan = new PlanEntity();
        plan.setCode(code);
        return plan;
    }
}
