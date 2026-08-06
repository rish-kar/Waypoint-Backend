package com.waypoint.backend.controller.entitlement;

import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.service.entitlement.EntitlementService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/entitlements")
public class EntitlementController {
    private final EntitlementService entitlementService;

    public EntitlementController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @GetMapping
    public EntitlementResponse current(@AuthenticationPrincipal UUID userId) {
        return entitlementService.currentEntitlement(userId, true);
    }
}
