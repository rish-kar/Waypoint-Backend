package com.waypoint.backend.controller.user;

import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.plan.PlanResponse;
import com.waypoint.backend.model.subscription.SubscriptionSnapshot;
import com.waypoint.backend.model.user.AccountResponse;
import com.waypoint.backend.model.user.AccountUpdateRequest;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.service.billing.PricingDisplayService;
import com.waypoint.backend.service.entitlement.EntitlementService;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.service.subscription.SubscriptionService;
import com.waypoint.backend.service.user.UserService;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final UserService userService;
    private final EntitlementService entitlementService;
    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final PricingDisplayService pricingDisplayService;

    public AccountController(
            UserService userService,
            EntitlementService entitlementService,
            PlanService planService,
            SubscriptionService subscriptionService,
            PricingDisplayService pricingDisplayService
    ) {
        this.userService = userService;
        this.entitlementService = entitlementService;
        this.planService = planService;
        this.subscriptionService = subscriptionService;
        this.pricingDisplayService = pricingDisplayService;
    }

    @GetMapping
    public AccountResponse account(
            @AuthenticationPrincipal UUID userId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return response(userId, userService.requireById(userId), acceptLanguage);
    }

    @PatchMapping
    public AccountResponse updateAccount(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AccountUpdateRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return response(
                userId,
                userService.updatePhoneNumber(userId, request.phoneNumber(), request.phoneCountryCode()),
                acceptLanguage
        );
    }

    private AccountResponse response(UUID userId, UserEntity user, String acceptLanguage) {
        SubscriptionSnapshot subscription = subscriptionService.current(userId);
        EntitlementResponse entitlement = entitlementService.fromSnapshot(subscription, false);
        String locale = pricingDisplayService.resolveLocale(user.getLocale(), acceptLanguage);
        PlanResponse plan = pricingDisplayService.localize(
                PlanResponse.from(planService.synchronizeUserPlan(user, subscription)),
                user.getLocale(),
                acceptLanguage
        );
        return new AccountResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPictureUrl(),
                user.getPhoneNumber(),
                user.getPhoneCountryCode(),
                locale,
                plan,
                entitlement
        );
    }
}
