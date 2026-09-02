package com.waypoint.backend.service.ai;

import com.waypoint.backend.config.ai.FamilyAiAccessProperties;
import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.FamilyAiUsageResponse;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.utilities.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FamilyAiBudgetServiceTests {
    private SpecialPremiumGrantRepository grantRepository;
    private PlanRepository planRepository;
    private FamilyAiBudgetService service;
    private UUID userId;
    private SpecialPremiumGrantEntity grant;

    @BeforeEach
    void setUp() {
        grantRepository = mock(SpecialPremiumGrantRepository.class);
        planRepository = mock(PlanRepository.class);
        service = new FamilyAiBudgetService(
                new FamilyAiAccessProperties(
                        5_000,
                        new BigDecimal("100"),
                        new BigDecimal("0.05"),
                        new BigDecimal("0.40")
                ),
                grantRepository,
                planRepository
        );
        userId = UUID.randomUUID();
        grant = new SpecialPremiumGrantEntity();
        grant.setActive(true);
        when(grantRepository.findByUserId(userId)).thenReturn(Optional.of(grant));
        when(grantRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(grant));
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString())).thenReturn(0L);
        when(planRepository.findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL)).thenReturn(Optional.of(new PlanEntity()));
    }

    @Test
    void countsInputWithTheGpt5Tokenizer() {
        AiChatRequest request = new AiChatRequest("hello", "", "", "", List.of(), null);
        assertThat(service.estimateInputTokens(request)).isEqualTo(1);
    }

    @Test
    void dividesTheFiveThousandRupeePoolAcrossCurrentSpecialUsers() {
        when(grantRepository.countActiveAt(any())).thenReturn(50L, 5L);

        FamilyAiUsageResponse fiftyUsers = service.current(userId);
        FamilyAiUsageResponse fiveUsers = service.current(userId);

        assertThat(fiftyUsers.activeSpecialUsers()).isEqualTo(50);
        assertThat(fiftyUsers.requestTokenLimit()).isEqualTo(5_000);
        assertThat(fiftyUsers.monthlyPoolMicrorupees()).isEqualTo(5_000L * 1_000_000L);
        assertThat(fiftyUsers.monthlyAllowanceMicrorupees()).isEqualTo(100L * 1_000_000L);
        assertThat(fiveUsers.activeSpecialUsers()).isEqualTo(5);
        assertThat(fiveUsers.monthlyAllowanceMicrorupees()).isEqualTo(1_000L * 1_000_000L);
    }

    @Test
    void serializesAndDebitsThePremiumSpecialGrant() {
        when(grantRepository.countActiveAt(any())).thenReturn(1L);

        assertThat(service.consumeRequestBudget(userId, 5_000, 1, 1_200)).isTrue();

        assertThat(grant.getAiPeriodKey()).isEqualTo(currentPeriod());
        assertThat(grant.getAiSpentMicrorupees()).isPositive();
        verify(planRepository).findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL);
        verify(grantRepository).findByUserIdForUpdate(userId);
        verify(grantRepository).save(grant);
    }

    @Test
    void rejectsPremiumSpecialRequestAboveFiveThousandTokensBeforeProviderBudgetLock() {
        assertThatThrownBy(() -> service.consumeRequestBudget(userId, 5_001, 1, 800))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("FAMILY_AI_REQUEST_TOO_LARGE");
                });

        verify(planRepository, never()).findByCodeForUpdate(any());
        verify(grantRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void rejectsRequestsThatWouldCrossTheSharedMonthlyPool() {
        when(grantRepository.countActiveAt(any())).thenReturn(1L);
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString()))
                .thenReturn(5_000L * 1_000_000L - 1L);

        assertThatThrownBy(() -> service.consumeRequestBudget(userId, 100, 1, 800))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("FAMILY_AI_BUDGET_REACHED");
                });
    }

    @Test
    void rejectsRequestsThatWouldCrossTheUsersDynamicShare() {
        when(grantRepository.countActiveAt(any())).thenReturn(50L);
        grant.setAiPeriodKey(currentPeriod());
        grant.setAiSpentMicrorupees(100L * 1_000_000L - 1L);
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString()))
                .thenReturn(grant.getAiSpentMicrorupees());

        assertThatThrownBy(() -> service.consumeRequestBudget(userId, 100, 1, 800))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("FAMILY_AI_BUDGET_REACHED");
                });
    }

    @Test
    void resetsTheUsersCounterWhenTheMonthChanges() {
        when(grantRepository.countActiveAt(any())).thenReturn(1L);
        grant.setAiPeriodKey(YearMonth.now(ZoneOffset.UTC).minusMonths(1).toString());
        grant.setAiSpentMicrorupees(4_999L * 1_000_000L);
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString())).thenReturn(0L);

        assertThat(service.consumeRequestBudget(userId, 100, 1, 800)).isTrue();

        assertThat(grant.getAiPeriodKey()).isEqualTo(currentPeriod());
        assertThat(grant.getAiSpentMicrorupees()).isPositive();
        assertThat(grant.getAiSpentMicrorupees()).isLessThan(4_999L * 1_000_000L);
    }

    @Test
    void previouslySpentGlobalBudgetStillBlocksLaterRequests() {
        when(grantRepository.countActiveAt(any())).thenReturn(5L);
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString()))
                .thenReturn(5_000L * 1_000_000L);

        assertThatThrownBy(() -> service.consumeRequestBudget(userId, 100, 1, 800))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("FAMILY_AI_BUDGET_REACHED"));
    }

    @Test
    void expiredSpecialAccessDoesNotConsumeOrCapTheRequest() {
        grant.setValidUntil(Instant.now().minusSeconds(60));

        assertThat(service.consumeRequestBudget(userId, 50_000, 4, 1_200)).isFalse();

        verify(planRepository, never()).findByCodeForUpdate(any());
        verify(grantRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void leavesNormalPaidUsersOutsideTheFamilyBudgetAndRequestCap() {
        when(grantRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.consumeRequestBudget(userId, 50_000, 4, 1_200)).isFalse();

        verify(planRepository, never()).findByCodeForUpdate(any());
        verify(grantRepository, never()).findByUserIdForUpdate(any());
    }

    private String currentPeriod() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }
}
