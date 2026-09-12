package com.waypoint.backend.controller.admin;

import com.waypoint.backend.service.admin.AdminDataDeletionService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminDataDeletionController {
    private final AdminDataDeletionService deletionService;

    public AdminDataDeletionController(AdminDataDeletionService deletionService) {
        this.deletionService = deletionService;
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID userId, Authentication authentication) {
        deletionService.deleteUser(userId, authentication.getName());
    }

    @PostMapping("/users/{userId}/reset-trial-state")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetTrialState(@PathVariable UUID userId, Authentication authentication) {
        deletionService.resetTrialState(userId, authentication.getName());
    }

    @DeleteMapping("/subscriptions/{subscriptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSubscription(@PathVariable UUID subscriptionId, Authentication authentication) {
        deletionService.deleteSubscription(subscriptionId, authentication.getName());
    }

    @DeleteMapping("/webhook-events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWebhookEvent(@PathVariable UUID eventId, Authentication authentication) {
        deletionService.deleteWebhookEvent(eventId, authentication.getName());
    }
}
