package com.waypoint.backend.service.ai;

import com.waypoint.backend.config.ai.FamilyAiAccessProperties;
import com.waypoint.backend.model.ai.FamilyAiPoolUsageEntity;
import com.waypoint.backend.model.ai.FamilyAiUsageResponse;
import com.waypoint.backend.model.ai.FamilyAiUserUsageEntity;
import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.repository.ai.FamilyAiPoolUsageRepository;
import com.waypoint.backend.repository.ai.FamilyAiUserUsageRepository;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.plan.PlanRepository;
import com.waypoint.backend.utilities.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
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
    private FamilyAiPoolUsageRepository poolRepository;
    private FamilyAiUserUsageRepository userUsageRepository;
    private FamilyAiBudgetService service;
    private UUID userId;
    private SpecialPremiumGrantEntity grant;

    @BeforeEach
    void setUp() {
        grantRepository = mock(SpecialPremiumGrantRepository.class);
        planRepository = mock(PlanRepository.class);
        poolRepository = mock(FamilyAiPoolUsageRepository.class);
        userUsageRepository = mock(FamilyAiUserUsageRepository.class);
        service = new FamilyAiBudgetService(
                new FamilyAiAccessProperties(
                        5_000,
                        new BigDecimal("100"),
                        new BigDecimal("0.05"),
                        new BigDecimal("0.40")
                ),
                grantRepository,
                planRepository,
                poolRepository,
                userUsageRepository
        );
        userId = UUID.randomUUID();
        grant = new SpecialPremiumGrantEntity();
        grant.setActive(true);
        when(grantRepository.findByUserId(userId)).thenReturn(Optional.of(grant));
        when(planRepository.findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL)).thenReturn(Optional.of(new PlanEntity()));
        when(poolRepository.findById(anyString())).thenReturn(Optional.empty());
        when(userUsageRepository.findByUserIdAndPeriodKey(any(UUID.class), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void dividesTheFiveThousandRupeePoolAcrossCurrentSpecialUsers() {
        when(grantRepository.countActiveAt(any())).thenReturn(50L, 5L);

        FamilyAiUsageResponse fiftyUsers = service.current(userId);
        FamilyAiUsageResponse fiveUsers = service.current(userId);

        assertThat(fiftyUsers.activeSpecialUsers()).isEqualTo(50);
        assertThat(fiftyUsers.monthlyPoolMicrorupees()).isEqualTo(5_000L * 1_000_000L);
        assertThat(fiftyUsers.monthlyAllowanceMicrorupees()).isEqualTo(100L * 1_000_000L);
        assertThat(fiveUsers.activeSpecialUsers()).isEqualTo(5);
        assertThat(fiveUsers.monthlyAllowanceMicrorupees()).isEqualTo(1_000L * 1_000_000L);
    }

    @Test
    void atomicallyDebitsTheSharedAndIndividualBudgets() {
        when(grantRepository.countActiveAt(any())).thenReturn(1L);
        FamilyAiPoolUsageEntity pool = new FamilyAiPoolUsageEntity();
        pool.setPeriodKey("ignored");
        FamilyAiUserUsageEntity usage = new FamilyAiUserUsageEntity();
        when(poolRepository.findById(anyString())).thenReturn(Optional.of(pool));
        when(userUsageRepository.findByUserIdAndPeriodKey(any(UUID.class), anyString())).thenReturn(Optional.of(usage));

        assertThat(service.consumeRequestBudget(userId, 50_000, 1, 1_200)).isTrue();

        assertThat(pool.getSpentMicrorupees()).isPositive();
        assertThat(usage.getSpentMicrorupees()).isEqualTo(pool.getSpentMicrorupees());
        verify(planRepository).findByCodeForUpdate(PlanCode.PREMIUM_SPECIAL);
        verify(poolRepository).save(pool);
        verify(userUsageRepository).save(usage);
    }

    @Test
    void rejectsRequestsThatWouldCrossTheSharedMonthlyPool() {
        when(grantRepository.countActiveAt(any())).thenReturn(1L);
        FamilyAiPoolUsageEntity pool = new FamilyAiPoolUsageEntity();
        pool.setPeriodKey("ignored");
        pool.setSpentMicrorupees(5_000L * 1_000_000L - 1L);
        FamilyAiUserUsageEntity usage = new FamilyAiUserUsageEntity();
        usage.setSpentMicrorupees(0L);
        when(poolRepository.findById(anyString())).thenReturn(Optional.of(pool));
        when(userUsageRepository.findByUserIdAndPeriodKey(any(UUID.class), anyString())).thenReturn(Optional.of(usage));

        assertThatThrownBy(() -> service.consumeRequestBudget(userId, 100, 1, 800))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("FAMILY_AI_BUDGET_REACHED");
                });
    }

    @Test
    void rejectsRequestsThatWouldCrossTheUsersDynamicShare() {
        when(grantRepository.countActiveAt(any())).thenReturn(50L);
        FamilyAiPoolUsageEntity pool = new FamilyAiPoolUsageEntity();
        pool.setPeriodKey("ignored");
        FamilyAiUserUsageEntity usage = new FamilyAiUserUsageEntity();
        usage.setSpentMicrorupees(100L * 1_000_000L - 1L);
        when(poolRepository.findById(anyString())).thenReturn(Optional.of(pool));
        when(userUsageRepository.findByUserIdAndPeriodKey(any(UUID.class), anyString())).thenReturn(Optional.of(usage));

        assertThatThrownBy(() -> service.consumeRequestBudget(userId, 100, 1, 800))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("FAMILY_AI_BUDGET_REACHED");
                });
    }

    @Test
    void expiredSpecialAccessDoesNotConsumeTheFamilyPool() {
        grant.setValidUntil(Instant.now().minusSeconds(60));

        assertThat(service.consumeRequestBudget(userId, 50_000, 4, 1_200)).isFalse();

        verify(planRepository, never()).findByCodeForUpdate(any());
        verify(poolRepository, never()).save(any());
    }

    @Test
    void leavesNormalPaidUsersOutsideTheFamilyBudget() {
        when(grantRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.consumeRequestBudget(userId, 50_000, 4, 1_200)).isFalse();

        verify(planRepository, never()).findByCodeForUpdate(any());
        verify(poolRepository, never()).save(any());
    }
}
