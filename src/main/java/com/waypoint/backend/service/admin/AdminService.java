package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminAuditEventResponse;
import com.waypoint.backend.model.admin.AdminOverviewResponse;
import com.waypoint.backend.model.admin.AdminPageResponse;
import com.waypoint.backend.model.admin.AdminPlanResponse;
import com.waypoint.backend.model.admin.AdminSubscriptionResponse;
import com.waypoint.backend.model.admin.AdminSubscriptionUpdateRequest;
import com.waypoint.backend.model.admin.AdminUserResponse;
import com.waypoint.backend.model.admin.AdminWebhookEventResponse;
import com.waypoint.backend.model.admin.AdminWebhookEventUpdateRequest;
import com.waypoint.backend.model.admin.PremiumSpecialGrantRequest;
import com.waypoint.backend.model.admin.PremiumSpecialGrantResponse;
import com.waypoint.backend.model.admin.PremiumSpecialSummaryResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.subscription.SubscriptionStatus;
import com.waypoint.backend.model.webhook.ProcessingStatus;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {
    private final AdminOverviewService overviewService;
    private final AdminUserService userService;
    private final AdminSubscriptionService subscriptionService;
    private final AdminGrantService grantService;
    private final AdminWebhookService webhookService;
    private final AdminCatalogService catalogService;

    public AdminService(
            AdminOverviewService overviewService,
            AdminUserService userService,
            AdminSubscriptionService subscriptionService,
            AdminGrantService grantService,
            AdminWebhookService webhookService,
            AdminCatalogService catalogService
    ) {
        this.overviewService = overviewService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.grantService = grantService;
        this.webhookService = webhookService;
        this.catalogService = catalogService;
    }

    public AdminOverviewResponse overview() {
        return overviewService.overview();
    }

    public AdminPageResponse<AdminUserResponse> users(
            String q, String provider, PlanCode plan, Boolean premium,
            Instant createdFrom, Instant createdTo, Instant lastLoginFrom, Instant lastLoginTo,
            int page, int size, String sort, String direction
    ) {
        return userService.users(q, provider, plan, premium, createdFrom, createdTo, lastLoginFrom, lastLoginTo,
                page, size, sort, direction);
    }

    public AdminUserResponse user(UUID userId) {
        return userService.user(userId);
    }

    public AdminUserResponse userByEmail(String email) {
        return userService.userByEmail(email);
    }

    public AdminPageResponse<AdminSubscriptionResponse> subscriptions(
            UUID userId, String email, String provider, String plan, SubscriptionStatus status,
            String externalCustomerId, String externalSubscriptionId,
            Instant createdFrom, Instant createdTo, Instant updatedFrom, Instant updatedTo,
            int page, int size, String sort, String direction
    ) {
        return subscriptionService.subscriptions(userId, email, provider, plan, status, externalCustomerId,
                externalSubscriptionId, createdFrom, createdTo, updatedFrom, updatedTo, page, size, sort, direction);
    }

    public AdminSubscriptionResponse subscription(UUID subscriptionId) {
        return subscriptionService.subscription(subscriptionId);
    }

    public AdminSubscriptionResponse updateSubscription(
            UUID subscriptionId,
            AdminSubscriptionUpdateRequest request,
            String adminId
    ) {
        return subscriptionService.updateSubscription(subscriptionId, request, adminId);
    }

    public PremiumSpecialGrantResponse grantPremiumSpecial(
            UUID userId,
            PremiumSpecialGrantRequest request,
            String adminId
    ) {
        return grantService.grantPremiumSpecial(userId, request, adminId);
    }

    public PremiumSpecialGrantResponse revokePremiumSpecial(UUID userId, String adminId) {
        return grantService.revokePremiumSpecial(userId, adminId);
    }

    public PremiumSpecialSummaryResponse premiumSpecialUsers() {
        return grantService.premiumSpecialUsers();
    }

    public AdminPageResponse<PremiumSpecialGrantResponse> specialGrants(
            UUID userId, String email, Boolean active, String grantedBy,
            Instant grantedFrom, Instant grantedTo, int page, int size, String sort, String direction
    ) {
        return grantService.specialGrants(userId, email, active, grantedBy, grantedFrom, grantedTo,
                page, size, sort, direction);
    }

    public PremiumSpecialGrantResponse specialGrant(UUID grantId) {
        return grantService.specialGrant(grantId);
    }

    public AdminPageResponse<AdminWebhookEventResponse> webhookEvents(
            String eventName, ProcessingStatus processingStatus, String externalObjectId,
            Instant receivedFrom, Instant receivedTo, boolean includePayload,
            int page, int size, String sort, String direction
    ) {
        return webhookService.webhookEvents(eventName, processingStatus, externalObjectId, receivedFrom, receivedTo,
                includePayload, page, size, sort, direction);
    }

    public AdminWebhookEventResponse webhookEvent(UUID eventId) {
        return webhookService.webhookEvent(eventId);
    }

    public AdminWebhookEventResponse updateWebhookEvent(
            UUID eventId,
            AdminWebhookEventUpdateRequest request,
            String adminId
    ) {
        return webhookService.updateWebhookEvent(eventId, request, adminId);
    }

    public List<AdminPlanResponse> plans() {
        return catalogService.plans();
    }

    public AdminPageResponse<AdminAuditEventResponse> auditEvents(
            String adminId, String action, String resourceType, String resourceId,
            Instant createdFrom, Instant createdTo, int page, int size, String sort, String direction
    ) {
        return catalogService.auditEvents(adminId, action, resourceType, resourceId, createdFrom, createdTo,
                page, size, sort, direction);
    }
}