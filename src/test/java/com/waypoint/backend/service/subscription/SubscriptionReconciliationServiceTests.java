package com.waypoint.backend.service.subscription;

import com.waypoint.backend.model.subscription.ProviderSubscriptionSnapshot;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.plan.PlanService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionReconciliationServiceTests {
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionAccessPolicy subscriptionAccessPolicy;
    @Mock
    private PlanService planService;

    @Test
    void recoversMissingLocalSubscriptionByExactAccountEmail() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        Instant trialEndsAt = Instant.parse("2026-08-20T18:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-14T18:00:00Z");
        ProviderSubscriptionSnapshot snapshot = new ProviderSubscriptionSnapshot(
                "sub_1", "User@Example.com", "cus_1", "prod_1", "111", "on_trial",
                trialEndsAt, Instant.parse("2030-01-01T00:00:00Z"), null, updatedAt
        );
        when(subscriptionRepository.findByExternalSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionAccessPolicy.planForVariant("111")).thenReturn("MONTHLY");

        SubscriptionReconciliationService service = service();
        assertEquals(SubscriptionReconciliationService.Result.APPLIED, service.reconcile(snapshot));

        ArgumentCaptor<SubscriptionEntity> captor = ArgumentCaptor.forClass(SubscriptionEntity.class);
        verify(subscriptionRepository).save(captor.capture());
        SubscriptionEntity saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals("sub_1", saved.getExternalSubscriptionId());
        assertEquals("MONTHLY", saved.getPlan());
        assertEquals(SubscriptionStatus.ON_TRIAL, saved.getStatus());
        assertEquals(trialEndsAt, saved.getTrialEndsAt());
        assertEquals(updatedAt, saved.getLastProviderEventAt());
        verify(planService).synchronizeUserPlan(user);
    }

    @Test
    void doesNotApplyOlderOrEqualProviderSnapshot() {
        Instant current = Instant.parse("2026-08-14T18:00:00Z");
        SubscriptionEntity existing = new SubscriptionEntity();
        existing.setLastProviderEventAt(current);
        when(subscriptionRepository.findByExternalSubscriptionIdForUpdate("sub_1"))
                .thenReturn(Optional.of(existing));

        ProviderSubscriptionSnapshot older = new ProviderSubscriptionSnapshot(
                "sub_1", "user@example.com", "cus_1", "prod_1", "111", "expired",
                null, null, null, current.minusSeconds(1)
        );
        assertEquals(SubscriptionReconciliationService.Result.STALE, service().reconcile(older));

        ProviderSubscriptionSnapshot equal = new ProviderSubscriptionSnapshot(
                "sub_1", "user@example.com", "cus_1", "prod_1", "111", "expired",
                null, null, null, current
        );
        assertEquals(SubscriptionReconciliationService.Result.STALE, service().reconcile(equal));

        verify(subscriptionRepository, never()).save(existing);
        verify(planService, never()).synchronizeUserPlan(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsSubscriptionWhenNoWaypointUserMatchesEmail() {
        Instant updatedAt = Instant.parse("2026-08-14T18:00:00Z");
        when(subscriptionRepository.findByExternalSubscriptionIdForUpdate("sub_2")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        ProviderSubscriptionSnapshot snapshot = new ProviderSubscriptionSnapshot(
                "sub_2", "unknown@example.com", "cus_2", "prod_2", "111", "active",
                null, null, null, updatedAt
        );

        assertEquals(SubscriptionReconciliationService.Result.UNMATCHED_USER, service().reconcile(snapshot));
        verify(subscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private SubscriptionReconciliationService service() {
        return new SubscriptionReconciliationService(
                subscriptionRepository,
                userRepository,
                subscriptionAccessPolicy,
                planService
        );
    }
}
