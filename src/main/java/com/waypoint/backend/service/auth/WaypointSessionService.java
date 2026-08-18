package com.waypoint.backend.service.auth;

import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.SessionResponse;
import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.model.user.UserResponse;
import com.waypoint.backend.security.jwt.JwtService;
import com.waypoint.backend.service.entitlement.EntitlementService;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.service.user.UserService;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WaypointSessionService {
    private final UserService userService;
    private final PlanService planService;
    private final JwtService jwtService;
    private final EntitlementService entitlementService;

    public WaypointSessionService(UserService userService, PlanService planService, JwtService jwtService,
                                  EntitlementService entitlementService) {
        this.userService = userService;
        this.planService = planService;
        this.jwtService = jwtService;
        this.entitlementService = entitlementService;
    }

    public AuthResponse issue(UUID userId) {
        UserEntity user = userService.requireById(userId);
        planService.synchronizeUserPlan(user);
        EntitlementResponse entitlement = entitlementService.currentEntitlement(userId, false);
        String waypointToken = jwtService.issueToken(user.getId(), user.getEmail());
        return new AuthResponse(waypointToken, "Bearer", jwtService.expirationSeconds(), UserResponse.from(user), entitlement);
    }

    public SessionResponse current(UUID userId) {
        if (userId == null) return SessionResponse.signedOut();
        UserEntity user = userService.requireById(userId);
        planService.synchronizeUserPlan(user);
        EntitlementResponse entitlement = entitlementService.currentEntitlement(userId, false);
        return new SessionResponse(true, UserResponse.from(user), entitlement);
    }
}
