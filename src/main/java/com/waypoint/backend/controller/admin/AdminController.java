package com.waypoint.backend.controller.admin;

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
import com.waypoint.backend.service.admin.AdminOverviewService;
import com.waypoint.backend.service.admin.AdminService;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;
    private final AdminOverviewService adminOverviewService;

    public AdminController(AdminService adminService, AdminOverviewService adminOverviewService) {
        this.adminService = adminService;
        this.adminOverviewService = adminOverviewService;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return adminOverviewService.overview();
    }

    @GetMapping(value = "/users", params = "!email")
    public AdminPageResponse<AdminUserResponse> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) PlanCode plan,
            @RequestParam(required = false) Boolean premium,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastLoginFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastLoginTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return adminService.users(
                q, provider, plan, premium, createdFrom, createdTo, lastLoginFrom, lastLoginTo,
                page, size, sort, direction
        );
    }

    @GetMapping(value = "/users", params = "email")
    public AdminUserResponse userByEmailCompatibility(@RequestParam String email) {
        return adminService.userByEmail(email);
    }

    @GetMapping("/users/by-email")
    public AdminUserResponse userByEmail(@RequestParam String email) {
        return adminService.userByEmail(email);
    }

    @GetMapping("/users/{userId}")
    public AdminUserResponse user(@PathVariable UUID userId) {
        return adminService.user(userId);
    }

    @GetMapping("/subscriptions")
    public AdminPageResponse<AdminSubscriptionResponse> subscriptions(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) String externalCustomerId,
            @RequestParam(required = false) String externalSubscriptionId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return adminService.subscriptions(
                userId, email, provider, plan, status, externalCustomerId, externalSubscriptionId,
                createdFrom, createdTo, updatedFrom, updatedTo, page, size, sort, direction
        );
    }

    @GetMapping("/subscriptions/{subscriptionId}")
    public AdminSubscriptionResponse subscription(@PathVariable UUID subscriptionId) {
        return adminService.subscription(subscriptionId);
    }

    @PatchMapping("/subscriptions/{subscriptionId}")
    public AdminSubscriptionResponse updateSubscription(
            @PathVariable UUID subscriptionId,
            @RequestBody AdminSubscriptionUpdateRequest request,
            Authentication authentication
    ) {
        return adminService.updateSubscription(subscriptionId, request, authentication.getName());
    }

    @PutMapping("/users/{userId}/premium-special")
    public PremiumSpecialGrantResponse grantPremiumSpecial(
            @PathVariable UUID userId,
            @Valid @RequestBody PremiumSpecialGrantRequest request,
            Authentication authentication
    ) {
        return adminService.grantPremiumSpecial(userId, request, authentication.getName());
    }

    @DeleteMapping("/users/{userId}/premium-special")
    public PremiumSpecialGrantResponse revokePremiumSpecial(
            @PathVariable UUID userId,
            Authentication authentication
    ) {
        return adminService.revokePremiumSpecial(userId, authentication.getName());
    }

    @GetMapping("/premium-special")
    public PremiumSpecialSummaryResponse premiumSpecialUsers() {
        return adminService.premiumSpecialUsers();
    }

    @GetMapping("/special-grants")
    public AdminPageResponse<PremiumSpecialGrantResponse> specialGrants(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String grantedBy,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant grantedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant grantedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return adminService.specialGrants(
                userId, email, active, grantedBy, grantedFrom, grantedTo, page, size, sort, direction
        );
    }

    @GetMapping("/special-grants/{grantId}")
    public PremiumSpecialGrantResponse specialGrant(@PathVariable UUID grantId) {
        return adminService.specialGrant(grantId);
    }

    @GetMapping("/webhook-events")
    public AdminPageResponse<AdminWebhookEventResponse> webhookEvents(
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) ProcessingStatus processingStatus,
            @RequestParam(required = false) String externalObjectId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant receivedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant receivedTo,
            @RequestParam(defaultValue = "false") boolean includePayload,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return adminService.webhookEvents(
                eventName, processingStatus, externalObjectId, receivedFrom, receivedTo, includePayload,
                page, size, sort, direction
        );
    }

    @GetMapping("/webhook-events/{eventId}")
    public AdminWebhookEventResponse webhookEvent(@PathVariable UUID eventId) {
        return adminService.webhookEvent(eventId);
    }

    @PatchMapping("/webhook-events/{eventId}")
    public AdminWebhookEventResponse updateWebhookEvent(
            @PathVariable UUID eventId,
            @RequestBody AdminWebhookEventUpdateRequest request,
            Authentication authentication
    ) {
        return adminService.updateWebhookEvent(eventId, request, authentication.getName());
    }

    @GetMapping("/plans")
    public List<AdminPlanResponse> plans() {
        return adminService.plans();
    }

    @GetMapping("/audit-events")
    public AdminPageResponse<AdminAuditEventResponse> auditEvents(
            @RequestParam(required = false) String adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return adminService.auditEvents(
                adminId, action, resourceType, resourceId, createdFrom, createdTo,
                page, size, sort, direction
        );
    }
}
