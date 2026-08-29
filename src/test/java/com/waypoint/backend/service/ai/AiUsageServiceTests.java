package com.waypoint.backend.service.ai;

import com.waypoint.backend.model.ai.AiUsageResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.entitlement.FeatureCatalog;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiUsageServiceTests {
    private UserRepository userRepository;
    private SubscriptionService subscriptionService;
    private AiUsageService service;
    private UUID userId;
    private UserEntity user;
    private Instant now;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        subscriptionService = mock(SubscriptionService.class);
        service = new AiUsageService(userRepository, subscriptionService, new FeatureCatalog());
        userId = UUID.randomUUID();
        user = new UserEntity();
        user.setId(userId);
        now = Instant.parse("2026-08-24T12:00:00Z");
    }

    @Test
    void exposesRemainingTrialRequests() {
        user.setAiTrialRequestsUsed(7);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.current(userId)).thenReturn(trialSnapshot());

        AiUsageResponse result = service.current(userId);

        assertThat(result.cloudAiAllowed()).isTrue();
        assertThat(result.trialLimited()).isTrue();
        assertThat(result.trialLimit()).isEqualTo(20);
        assertThat(result.trialUsed()).isEqualTo(7);
        assertThat(result.trialRemaining()).isEqualTo(13);
        assertThat(result.subscriptionStatus()).isEqualTo("ON_TRIAL");
    }

    @Test
    void consumesExactlyOneTrialRequest() {
        user.setAiTrialRequestsUsed(19);
        when(subscriptionService.current(userId)).thenReturn(trialSnapshot());
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        AiUsageResponse result = service.consume(userId);

        assertThat(user.getAiTrialRequestsUsed()).isEqualTo(20);
        assertThat(result.trialRemaining()).isZero();
        assertThat(result.cloudAiAllowed()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void rejectsTrialRequestAfterTwentyHaveBeenUsed() {
        user.setAiTrialRequestsUsed(20);
        when(subscriptionService.current(userId)).thenReturn(trialSnapshot());
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.consume(userId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("AI_TRIAL_LIMIT_REACHED");
                });

        assertThat(user.getAiTrialRequestsUsed()).isEqualTo(20);
        verify(userRepository, never()).save(user);
    }

    @Test
    void paidPremiumUsageIsNotDeductedFromTrialCounter() {
        user.setAiTrialRequestsUsed(9);
        when(subscriptionService.current(userId)).thenReturn(activePremiumSnapshot());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AiUsageResponse result = service.consume(userId);

        assertThat(result.cloudAiAllowed()).isTrue();
        assertThat(result.trialLimited()).isFalse();
        assertThat(user.getAiTrialRequestsUsed()).isEqualTo(9);
        verify(userRepository, never()).save(user);
    }

    @Test
    void freeAccountCannotConsumeCloudAi() {
        when(subscriptionService.current(userId)).thenReturn(freeSnapshot());

        assertThatThrownBy(() -> service.consume(userId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.code()).isEqualTo("AI_ACCESS_DENIED");
                });
    }

    private SubscriptionSnapshot trialSnapshot() {
        Instant trialEnd = now.plusSeconds(86_400);
        return new SubscriptionSnapshot(
                PlanCode.PREMIUM_MONTHLY,
                SubscriptionStatus.ON_TRIAL,
                true,
                "sub-trial",
                trialEnd,
                null,
                null,
                trialEnd,
                now
        );
    }

    private SubscriptionSnapshot activePremiumSnapshot() {
        Instant validUntil = now.plusSeconds(86_400);
        return new SubscriptionSnapshot(
                PlanCode.PREMIUM_MONTHLY,
                SubscriptionStatus.ACTIVE,
                true,
                "sub-paid",
                null,
                validUntil,
                null,
                validUntil,
                now
        );
    }

    private SubscriptionSnapshot freeSnapshot() {
        return new SubscriptionSnapshot(
                PlanCode.FREE,
                SubscriptionStatus.INACTIVE,
                false,
                null,
                null,
                null,
                null,
                null,
                now
        );
    }
}
