package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminFamilyAiUsageResponse;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.service.ai.FamilyAiBudgetService;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.utilities.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FamilyAiAdminService {
    private final FamilyAiBudgetService familyAiBudgetService;
    private final SubscriptionService subscriptionService;

    public FamilyAiAdminService(
            FamilyAiBudgetService familyAiBudgetService,
            SubscriptionService subscriptionService
    ) {
        this.familyAiBudgetService = familyAiBudgetService;
        this.subscriptionService = subscriptionService;
    }

    public AdminFamilyAiUsageResponse currentForAdminApi() {
        return familyAiBudgetService.adminCurrent();
    }

    public AdminFamilyAiUsageResponse currentForAuthenticatedAdmin(UUID userId) {
        if (userId == null || subscriptionService.current(userId).planCode() != PlanCode.ADMIN) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_ACCESS_DENIED",
                    "Admin access is required."
            );
        }
        return familyAiBudgetService.adminCurrent();
    }
}
