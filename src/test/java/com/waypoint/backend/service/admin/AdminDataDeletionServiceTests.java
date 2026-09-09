package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.entitlement.SpecialPremiumGrantEntity;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.model.webhook.WebhookEventEntity;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;
import com.waypoint.backend.service.plan.PlanService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDataDeletionServiceTests {
    private UserRepository userRepository;
    private SubscriptionRepository subscriptionRepository;
    private SpecialPremiumGrantRepository grantRepository;
    private WebhookEventRepository webhookEventRepository;
    private PlanService planService;
    private AdminAuditEventRepository auditEventRepository;
    private AdminDataDeletionService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        grantRepository = mock(SpecialPremiumGrantRepository.class);
        webhookEventRepository = mock(WebhookEventRepository.class);
        planService = mock(PlanService.class);
        auditEventRepository = mock(AdminAuditEventRepository.class);
        service = new AdminDataDeletionService(
                userRepository,
                subscriptionRepository,
                grantRepository,
                webhookEventRepository,
                planService,
                auditEventRepository
        );
    }

    @Test
    void deletesUserOwnedSubscriptionAndGrantBeforeUser() {
        UserEntity user = user();
        SubscriptionEntity subscription = new SubscriptionEntity();
        SpecialPremiumGrantEntity grant = new SpecialPremiumGrantEntity();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())).thenReturn(List.of(subscription));
        when(grantRepository.findByUserId(user.getId())).thenReturn(Optional.of(grant));

        service.deleteUser(user.getId(), "admin");

        verify(subscriptionRepository).deleteAll(List.of(subscription));
        verify(grantRepository).delete(grant);
        verify(userRepository).delete(user);
        verify(auditEventRepository).save(any());
    }

    @Test
    void deletesSubscriptionThenResynchronizesUserPlan() {
        UserEntity user = user();
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setExternalSubscriptionId("sub-test");
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        service.deleteSubscription(subscription.getId(), "admin");

        verify(subscriptionRepository).delete(subscription);
        verify(subscriptionRepository).flush();
        verify(planService).synchronizeUserPlan(user);
        verify(auditEventRepository).save(any());
    }

    @Test
    void deletesWebhookEvent() {
        WebhookEventEntity event = new WebhookEventEntity();
        event.setId(UUID.randomUUID());
        event.setEventName("subscription_updated");
        event.setExternalObjectId("sub-test");
        when(webhookEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        service.deleteWebhookEvent(event.getId(), "admin");

        verify(webhookEventRepository).delete(event);
        verify(webhookEventRepository).flush();
        verify(auditEventRepository).save(any());
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        return user;
    }
}
