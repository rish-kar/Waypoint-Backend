package com.waypoint.backend.controller.subscription;

import com.waypoint.backend.model.subscription.SubscriptionResponse;
import com.waypoint.backend.service.subscription.SubscriptionService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/current")
    public SubscriptionResponse current(@AuthenticationPrincipal UUID userId) {
        return SubscriptionResponse.from(subscriptionService.current(userId));
    }
}
