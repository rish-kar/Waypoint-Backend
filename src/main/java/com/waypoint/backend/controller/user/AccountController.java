package com.waypoint.backend.controller.user;

import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.model.user.AccountResponse;
import com.waypoint.backend.model.user.AccountUpdateRequest;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.service.entitlement.EntitlementService;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.service.user.UserService;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final UserService userService;
    private final EntitlementService entitlementService;
    private final PlanService planService;

    public AccountController(
            UserService userService,
            EntitlementService entitlementService,
            PlanService planService
    ) {
        this.userService = userService;
        this.entitlementService = entitlementService;
        this.planService = planService;
    }

    @GetMapping
    public AccountResponse account(@AuthenticationPrincipal UUID userId) {
        return response(userId, userService.requireById(userId));
    }

    @PatchMapping
    public AccountResponse updateAccount(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AccountUpdateRequest request
    ) {
        return response(
                userId,
                userService.updatePhoneNumber(userId, request.phoneNumber(), request.phoneCountryCode())
        );
    }

    private AccountResponse response(UUID userId, UserEntity user) {
        EntitlementResponse entitlement = entitlementService.currentEntitlement(userId, false);
        return new AccountResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPictureUrl(),
                user.getPhoneNumber(),
                user.getPhoneCountryCode(),
                PlanResponse.from(planService.effectivePlan(userId)),
                entitlement
        );
    }
}
