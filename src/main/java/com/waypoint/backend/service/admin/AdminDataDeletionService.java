package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminAuditEventEntity;
import com.waypoint.backend.model.subscription.SubscriptionEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.model.webhook.WebhookEventEntity;
import com.waypoint.backend.repository.admin.AdminAuditEventRepository;
import com.waypoint.backend.repository.entitlement.SpecialPremiumGrantRepository;
import com.waypoint.backend.repository.subscription.SubscriptionRepository;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.repository.webhook.WebhookEventRepository;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.utilities.exception.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminDataDeletionService {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SpecialPremiumGrantRepository grantRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PlanService planService;
    private final AdminAuditEventRepository auditEventRepository;

    public AdminDataDeletionService(
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            SpecialPremiumGrantRepository grantRepository,
            WebhookEventRepository webhookEventRepository,
            PlanService planService,
            AdminAuditEventRepository auditEventRepository
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.grantRepository = grantRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.planService = planService;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void deleteUser(UUID userId, String adminId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String email = user.getEmail();

        subscriptionRepository.deleteAll(subscriptionRepository.findByUserIdOrderByUpdatedAtDesc(userId));
        grantRepository.findByUserId(userId).ifPresent(grantRepository::delete);
        subscriptionRepository.flush();
        grantRepository.flush();

        userRepository.delete(user);
        userRepository.flush();
        audit(adminId, "DELETE_USER", "USER", userId, "email=" + email);
    }

    @Transactional
    public void deleteSubscription(UUID subscriptionId, String adminId) {
        SubscriptionEntity subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
        UserEntity user = subscription.getUser();
        String externalSubscriptionId = subscription.getExternalSubscriptionId();

        subscriptionRepository.delete(subscription);
        subscriptionRepository.flush();
        planService.synchronizeUserPlan(user);
        audit(
                adminId,
                "DELETE_SUBSCRIPTION",
                "SUBSCRIPTION",
                subscriptionId,
                "userId=" + user.getId() + ", externalSubscriptionId=" + externalSubscriptionId
        );
    }

    @Transactional
    public void deleteWebhookEvent(UUID eventId, String adminId) {
        WebhookEventEntity event = webhookEventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Webhook event not found"));
        String eventName = event.getEventName();
        String externalObjectId = event.getExternalObjectId();

        webhookEventRepository.delete(event);
        webhookEventRepository.flush();
        audit(
                adminId,
                "DELETE_WEBHOOK_EVENT",
                "WEBHOOK_EVENT",
                eventId,
                "eventName=" + eventName + ", externalObjectId=" + externalObjectId
        );
    }

    private void audit(String adminId, String action, String resourceType, UUID resourceId, String details) {
        AdminAuditEventEntity event = new AdminAuditEventEntity();
        event.setAdminId(adminId);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId.toString());
        event.setDetails(details);
        auditEventRepository.save(event);
    }
}
