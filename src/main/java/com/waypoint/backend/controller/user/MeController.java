package com.waypoint.backend.controller.user;

import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.model.user.MeResponse;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.service.entitlement.EntitlementService;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.service.user.UserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final UserService userService;
    private final EntitlementService entitlementService;
    private final PlanService planService;

    public MeController(
            UserService userService,
            EntitlementService entitlementService,
            PlanService planService
    ) {
        this.userService = userService;
        this.entitlementService = entitlementService;
        this.planService = planService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal UUID userId) {
        UserEntity user = userService.requireById(userId);
        planService.synchronizeUserPlan(user);
        EntitlementResponse entitlement = entitlementService.currentEntitlement(userId, false);
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPictureUrl(),
                PlanResponse.from(user.getPlan()),
                entitlement
        );
    }
}
