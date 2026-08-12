package com.waypoint.backend.controller.billing;

import com.waypoint.backend.model.billing.BillingStatusResponse;
import com.waypoint.backend.model.billing.CheckoutResponse;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.service.billing.BillingService;
import com.waypoint.backend.service.user.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final BillingService billingService;
    private final UserService userService;

    public BillingController(BillingService billingService, UserService userService) {
        this.billingService = billingService;
        this.userService = userService;
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@AuthenticationPrincipal UUID userId, @Valid @RequestBody CheckoutRequest request) {
        UserEntity user = userService.requireById(userId);
        String checkoutUrl = billingService.createCheckout(user, request.plan());
        return new CheckoutResponse(checkoutUrl);
    }

    @GetMapping("/status")
    public BillingStatusResponse status(@AuthenticationPrincipal UUID userId) {
        return billingService.billingStatus(userId);
    }

    public record CheckoutRequest(@NotNull CheckoutPlan plan) {
    }
}
