package com.waypoint.backend.service.ai;

import com.waypoint.backend.config.ai.FamilyAiAccessProperties;
import com.waypoint.backend.model.admin.AdminFamilyAiUsageResponse;
import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.FamilyAiUsageResponse;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.user.UserEntity;
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
    private static final long RUPEE = 1_000_000L;

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
                        5,
                        25,
                        new BigDecimal("100"),
                        new BigDecimal("0.05"),
                        new BigDecimal("0.40")
                ),
                grantRepository,
                planRepository
        );
        userId = UUID.randomUUID();
        grant = grant(userId, "special@example.com", "Special User", true);
        when(grantRepository.findByUserId(userId)).thenReturn(Optional.of(grant));
        when(grantRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(grant));
        when(grantRepository.findActiveAt(any())).thenReturn(List.of(grant));
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString())).thenReturn(0L);
        when(planRepository.findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL)).thenReturn(Optional.of(new PlanEntity()));
    }

    @Test
    void countsInputWithTheGpt5Tokenizer() {
        AiChatRequest request = new AiChatRequest("hello", "", "", "", List.of(), null);
        assertThat(service.estimateInputTokens(request)).isEqualTo(1);
    }

    @Test
    void userViewExposesFiveHourAndWeeklyPercentagesInsteadOfMoney() {
        grant.setAiSessionStartedAt(Instant.now().minusSeconds(60));
        grant.setAiSessionSpentMicrorupees(25L * RUPEE);
        grant.setAiWeeklyStartedAt(Instant.now().minusSeconds(60));
        grant.setAiWeeklySpentMicrorupees(125L * RUPEE);

        FamilyAiUsageResponse usage = service.current(userId);

        assertThat(usage.specialAccess()).isTrue();
        assertThat(usage.requestTokenLimit()).isEqualTo(5_000);
        assertThat(usage.sessionWindowHours()).isEqualTo(5);
        assertThat(usage.sessionUsagePercent()).isEqualTo(10.0);
        assertThat(usage.sessionResetsAt()).isAfter(Instant.now());
        assertThat(usage.weeklyWindowDays()).isEqualTo(7);
        assertThat(usage.weeklyUsagePercent()).isEqualTo(10.0);
        assertThat(usage.weeklyResetsAt()).isAfter(Instant.now());
        assertThat(usage.status()).isEqualTo("ACTIVE");
    }

    @Test
    void heavyRecentUserBorrowsUnusedQuotaFromIdleUser() {
        Instant now = Instant.now();
        SpecialPremiumGrantEntity idle = grant(UUID.randomUUID(), "idle@example.com", "Idle User", true);

        grant.setAiPeriodKey(currentPeriod());
        grant.setAiSpentMicrorupees(2_500L * RUPEE);
        grant.setAiWeeklyStartedAt(now.minusSeconds(60));
        grant.setAiWeeklySpentMicrorupees(625L * RUPEE);
        idle.setAiPeriodKey(currentPeriod());
        idle.setAiSpentMicrorupees(0L);

        when(grantRepository.findActiveAt(any())).thenReturn(List.of(grant, idle));
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString())).thenReturn(2_500L * RUPEE);

        FamilyAiUsageResponse usage = service.current(userId);

        // A fixed equal split would make 625 rupees equal 100% of this user's weekly limit.
        // Adaptive borrowing increases the heavy user's allowance while keeping part of the idle user's share protected.
        assertThat(usage.weeklyUsagePercent()).isLessThan(100.0);
        assertThat(usage.status()).isEqualTo("ACTIVE");
    }

    @Test
    void adminViewExposesFullPoolAndRollingLimitStats() {
        SpecialPremiumGrantEntity revoked = grant(UUID.randomUUID(), "revoked@example.com", "Revoked User", false);
        Instant now = Instant.now();
        grant.setAiPeriodKey(currentPeriod());
        grant.setAiSpentMicrorupees(100L * RUPEE);
        grant.setAiPeriodRequestCount(12L);
        grant.setAiPeriodInputTokens(24_000L);
        grant.setAiSessionStartedAt(now.minusSeconds(60));
        grant.setAiSessionSpentMicrorupees(25L * RUPEE);
        grant.setAiSessionRequestCount(3L);
        grant.setAiSessionInputTokens(6_000L);
        grant.setAiWeeklyStartedAt(now.minusSeconds(60));
        grant.setAiWeeklySpentMicrorupees(125L * RUPEE);
        grant.setAiWeeklyRequestCount(8L);
        grant.setAiWeeklyInputTokens(16_000L);
        revoked.setAiPeriodKey(currentPeriod());
        revoked.setAiSpentMicrorupees(500L * RUPEE);
        revoked.setRevokedBy("test-admin");
        revoked.setRevokedAt(now.minusSeconds(60));

        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString())).thenReturn(600L * RUPEE);
        when(grantRepository.findAllWithUserForFamilyAiAdmin()).thenReturn(List.of(grant, revoked));

        AdminFamilyAiUsageResponse usage = service.adminCurrent();

        assertThat(usage.monthlyPoolMicrorupees()).isEqualTo(5_000L * RUPEE);
        assertThat(usage.poolSpentMicrorupees()).isEqualTo(600L * RUPEE);
        assertThat(usage.activeSpecialUsers()).isEqualTo(1);
        assertThat(usage.sessionWindowHours()).isEqualTo(5);
        assertThat(usage.sessionBudgetPercent()).isEqualTo(5);
        assertThat(usage.weeklyWindowDays()).isEqualTo(7);
        assertThat(usage.weeklyBudgetPercent()).isEqualTo(25);
        assertThat(usage.users()).hasSize(2);
        assertThat(usage.users().get(0).monthlyRequestCount()).isEqualTo(12L);
        assertThat(usage.users().get(0).monthlyInputTokens()).isEqualTo(24_000L);
        assertThat(usage.users().get(0).sessionLimitMicrorupees()).isEqualTo(250L * RUPEE);
        assertThat(usage.users().get(0).sessionSpentMicrorupees()).isEqualTo(25L * RUPEE);
        assertThat(usage.users().get(0).sessionUsagePercent()).isEqualTo(10.0);
        assertThat(usage.users().get(0).sessionRequestCount()).isEqualTo(3L);
        assertThat(usage.users().get(0).weeklyLimitMicrorupees()).isEqualTo(1_250L * RUPEE);
        assertThat(usage.users().get(0).weeklySpentMicrorupees()).isEqualTo(125L * RUPEE);
        assertThat(usage.users().get(0).weeklyUsagePercent()).isEqualTo(10.0);
        assertThat(usage.users().get(0).weeklyRequestCount()).isEqualTo(8L);
        assertThat(usage.users().get(0).status()).isEqualTo("ACTIVE");
        assertThat(usage.users().get(1).status()).isEqualTo("REVOKED");
    }

    @Test
    void nonSpecialUsageIsZeroedWithoutReadingTheFamilyPool() {
        when(grantRepository.findByUserId(userId)).thenReturn(Optional.empty());

        FamilyAiUsageResponse usage = service.current(userId);

        assertThat(usage.specialAccess()).isFalse();
        assertThat(usage.status()).isEqualTo("NOT_SPECIAL");
        assertThat(usage.requestTokenLimit()).isZero();
        assertThat(usage.sessionUsagePercent()).isZero();
        assertThat(usage.weeklyUsagePercent()).isZero();
        verify(grantRepository, never()).findActiveAt(any());
        verify(grantRepository, never()).sumAiSpentMicrorupeesForPeriod(anyString());
    }

    @Test
    void debitUpdatesMonthlySessionAndWeeklyCountersAtomically() {
        assertThat(service.consumeRequestBudget(userId, 1_000, 1, 800)).isTrue();

        assertThat(grant.getAiPeriodKey()).isEqualTo(currentPeriod());
        assertThat(grant.getAiSpentMicrorupees()).isPositive();
        assertThat(grant.getAiPeriodRequestCount()).isEqualTo(1L);
        assertThat(grant.getAiPeriodInputTokens()).isEqualTo(1_000L);
        assertThat(grant.getAiSessionStartedAt()).isNotNull();
        assertThat(grant.getAiSessionSpentMicrorupees()).isEqualTo(grant.getAiSpentMicrorupees());
        assertThat(grant.getAiSessionRequestCount()).isEqualTo(1L);
        assertThat(grant.getAiSessionInputTokens()).isEqualTo(1_000L);
        assertThat(grant.getAiWeeklyStartedAt()).isNotNull();
        assertThat(grant.getAiWeeklySpentMicrorupees()).isEqualTo(grant.getAiSpentMicrorupees());
        assertThat(grant.getAiWeeklyRequestCount()).isEqualTo(1L);
        assertThat(grant.getAiWeeklyInputTokens()).isEqualTo(1_000L);
        verify(planRepository).findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL);
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
    void rejectsRequestsThatWouldCrossTheFiveHourLimit() {
        grant.setAiSessionStartedAt(Instant.now().minusSeconds(60));
        grant.setAiSessionSpentMicrorupees(250L * RUPEE - 1L);

        assertThatThrownBy(() -> service.consumeRequestBudget(userId, 100, 1, 800))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("FAMILY_AI_SESSION_LIMIT_REACHED");
                });
    }

    @Test
    void expiredFiveHourWindowStartsFreshOnNextRequest() {
        grant.setAiSessionStartedAt(Instant.now().minusSeconds(5 * 60 * 60 + 5));
        grant.setAiSessionSpentMicrorupees(250L * RUPEE);
        grant.setAiSessionRequestCount(99L);
        grant.setAiSessionInputTokens(999_999L);

        assertThat(service.consumeRequestBudget(userId, 100, 1, 800)).isTrue();

        assertThat(grant.getAiSessionStartedAt()).isAfter(Instant.now().minusSeconds(5));
        assertThat(grant.getAiSessionRequestCount()).isEqualTo(1L);
        assertThat(grant.getAiSessionInputTokens()).isEqualTo(100L);
        assertThat(grant.getAiSessionSpentMicrorupees()).isLessThan(250L * RUPEE);
    }

    @Test
    void rejectsRequestsThatWouldCrossTheWeeklyLimit() {
        grant.setAiWeeklyStartedAt(Instant.now().minusSeconds(60));
        grant.setAiWeeklySpentMicrorupees(1_250L * RUPEE - 1L);

        assertThatThrownBy(() -> service.consumeRequestBudget(userId, 100, 1, 800))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("FAMILY_AI_WEEKLY_LIMIT_REACHED");
                });
    }

    @Test
    void expiredWeeklyWindowStartsFreshOnNextRequest() {
        grant.setAiWeeklyStartedAt(Instant.now().minusSeconds(7 * 24 * 60 * 60 + 5));
        grant.setAiWeeklySpentMicrorupees(1_250L * RUPEE);
        grant.setAiWeeklyRequestCount(999L);
        grant.setAiWeeklyInputTokens(9_999_999L);

        assertThat(service.consumeRequestBudget(userId, 100, 1, 800)).isTrue();

        assertThat(grant.getAiWeeklyStartedAt()).isAfter(Instant.now().minusSeconds(5));
        assertThat(grant.getAiWeeklyRequestCount()).isEqualTo(1L);
        assertThat(grant.getAiWeeklyInputTokens()).isEqualTo(100L);
        assertThat(grant.getAiWeeklySpentMicrorupees()).isLessThan(1_250L * RUPEE);
    }

    @Test
    void sharedMonthlyPoolStillActsAsTheHardSafetyCap() {
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString()))
                .thenReturn(5_000L * RUPEE - 1L);

        assertThatThrownBy(() -> service.consumeRequestBudget(userId, 100, 1, 800))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("FAMILY_AI_ACCESS_LIMIT_REACHED"));
    }

    @Test
    void resetsMonthlyCountersWhenMonthChangesButKeepsIndependentRollingWindows() {
        grant.setAiPeriodKey(YearMonth.now(ZoneOffset.UTC).minusMonths(1).toString());
        grant.setAiSpentMicrorupees(4_999L * RUPEE);
        grant.setAiPeriodRequestCount(500L);
        grant.setAiPeriodInputTokens(500_000L);
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString())).thenReturn(0L);

        assertThat(service.consumeRequestBudget(userId, 100, 1, 800)).isTrue();

        assertThat(grant.getAiPeriodKey()).isEqualTo(currentPeriod());
        assertThat(grant.getAiSpentMicrorupees()).isLessThan(4_999L * RUPEE);
        assertThat(grant.getAiPeriodRequestCount()).isEqualTo(1L);
        assertThat(grant.getAiPeriodInputTokens()).isEqualTo(100L);
    }

    @Test
    void expiredSpecialAccessDoesNotConsumeOrCapTheRequest() {
        grant.setValidUntil(Instant.now().minusSeconds(60));
        assertThat(service.consumeRequestBudget(userId, 50_000, 4, 1_200)).isFalse();
        verify(planRepository, never()).findByCodeForUpdate(any());
        verify(grantRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void leavesNormalPaidUsersOutsideAllFamilyLimits() {
        when(grantRepository.findByUserId(userId)).thenReturn(Optional.empty());
        assertThat(service.consumeRequestBudget(userId, 50_000, 4, 1_200)).isFalse();
        verify(planRepository, never()).findByCodeForUpdate(any());
        verify(grantRepository, never()).findByUserIdForUpdate(any());
    }

    private SpecialPremiumGrantEntity grant(UUID id, String email, String name, boolean active) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName(name);
        user.setProvider("GOOGLE");
        user.setCreatedAt(Instant.now().minusSeconds(3_600));
        user.setLastLoginAt(Instant.now().minusSeconds(60));

        SpecialPremiumGrantEntity value = new SpecialPremiumGrantEntity();
        value.setId(UUID.randomUUID());
        value.setUser(user);
        value.setActive(active);
        value.setReason("Friends and family");
        value.setGrantedBy("test-admin");
        value.setGrantedAt(Instant.now().minusSeconds(600));
        return value;
    }

    private String currentPeriod() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }
}
