package com.waypoint.backend.user;

import com.waypoint.backend.entitlement.EntitlementResponse;
import com.waypoint.backend.entitlement.EntitlementService;
import com.waypoint.backend.plan.PlanResponse;
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

    public MeController(UserService userService, EntitlementService entitlementService) {
        this.userService = userService;
        this.entitlementService = entitlementService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal UUID userId) {
        UserEntity user = userService.requireById(userId);
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
