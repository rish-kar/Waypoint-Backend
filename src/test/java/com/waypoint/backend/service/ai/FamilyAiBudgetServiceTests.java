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
        grant = grant(userId, "special@example.com", "Special User", true);
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
    void dividesTheFiveThousandRupeePoolWithoutExposingPoolDetailsToTheUser() {
        when(grantRepository.countActiveAt(any())).thenReturn(50L, 5L);

        FamilyAiUsageResponse fiftyUsers = service.current(userId);
        FamilyAiUsageResponse fiveUsers = service.current(userId);

        assertThat(fiftyUsers.specialAccess()).isTrue();
        assertThat(fiftyUsers.requestTokenLimit()).isEqualTo(5_000);
        assertThat(fiftyUsers.monthlyAllowanceMicrorupees()).isEqualTo(100L * 1_000_000L);
        assertThat(fiveUsers.monthlyAllowanceMicrorupees()).isEqualTo(1_000L * 1_000_000L);
    }

    @Test
    void adminViewExposesFullPoolAndEverySpecialGrantUsage() {
        SpecialPremiumGrantEntity revoked = grant(UUID.randomUUID(), "revoked@example.com", "Revoked User", false);
        grant.setAiPeriodKey(currentPeriod());
        grant.setAiSpentMicrorupees(100L * 1_000_000L);
        revoked.setAiPeriodKey(currentPeriod());
        revoked.setAiSpentMicrorupees(500L * 1_000_000L);
        revoked.setRevokedBy("test-admin");
        revoked.setRevokedAt(Instant.now().minusSeconds(60));

        when(grantRepository.countActiveAt(any())).thenReturn(1L);
        when(grantRepository.sumAiSpentMicrorupeesForPeriod(anyString())).thenReturn(600L * 1_000_000L);
        when(grantRepository.findAllWithUserForFamilyAiAdmin()).thenReturn(List.of(grant, revoked));

        AdminFamilyAiUsageResponse usage = service.adminCurrent();

        assertThat(usage.monthlyPoolMicrorupees()).isEqualTo(5_000L * 1_000_000L);
        assertThat(usage.poolSpentMicrorupees()).isEqualTo(600L * 1_000_000L);
        assertThat(usage.poolRemainingMicrorupees()).isEqualTo(4_400L * 1_000_000L);
        assertThat(usage.activeSpecialUsers()).isEqualTo(1);
        assertThat(usage.requestTokenLimit()).isEqualTo(5_000);
        assertThat(usage.users()).hasSize(2);
        assertThat(usage.users().get(0).email()).isEqualTo("special@example.com");
        assertThat(usage.users().get(0).monthlyAllowanceMicrorupees()).isEqualTo(5_000L * 1_000_000L);
        assertThat(usage.users().get(0).spentMicrorupees()).isEqualTo(100L * 1_000_000L);
        assertThat(usage.users().get(0).remainingMicrorupees()).isEqualTo(4_400L * 1_000_000L);
        assertThat(usage.users().get(0).status()).isEqualTo("ACTIVE");
        assertThat(usage.users().get(1).email()).isEqualTo("revoked@example.com");
        assertThat(usage.users().get(1).monthlyAllowanceMicrorupees()).isZero();
        assertThat(usage.users().get(1).spentMicrorupees()).isEqualTo(500L * 1_000_000L);
        assertThat(usage.users().get(1).status()).isEqualTo("REVOKED");
    }

    @Test
    void nonSpecialUsageIsZeroedWithoutReadingTheFamilyPool() {
        when(grantRepository.findByUserId(userId)).thenReturn(Optional.empty());

        FamilyAiUsageResponse usage = service.current(userId);

        assertThat(usage.specialAccess()).isFalse();
        assertThat(usage.status()).isEqualTo("NOT_SPECIAL");
        assertThat(usage.requestTokenLimit()).isZero();
        assertThat(usage.monthlyAllowanceMicrorupees()).isZero();
        assertThat(usage.spentMicrorupees()).isZero();
        assertThat(usage.remainingMicrorupees()).isZero();
        verify(grantRepository, never()).countActiveAt(any());
        verify(grantRepository, never()).sumAiSpentMicrorupeesForPeriod(anyString());
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
