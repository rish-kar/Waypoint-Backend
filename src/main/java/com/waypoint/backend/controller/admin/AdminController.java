package com.waypoint.backend.controller.admin;

import com.waypoint.backend.model.admin.AdminUserResponse;
import com.waypoint.backend.model.admin.PremiumSpecialGrantRequest;
import com.waypoint.backend.model.admin.PremiumSpecialGrantResponse;
import com.waypoint.backend.model.admin.PremiumSpecialSummaryResponse;
import com.waypoint.backend.service.admin.AdminService;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PutMapping("/users/{userId}/premium-special")
    public PremiumSpecialGrantResponse grantPremiumSpecial(
            @PathVariable UUID userId,
            @Valid @RequestBody PremiumSpecialGrantRequest request,
            Authentication authentication
    ) {
        return adminService.grantPremiumSpecial(userId, request, authentication.getName());
    }

    @DeleteMapping("/users/{userId}/premium-special")
    public PremiumSpecialGrantResponse revokePremiumSpecial(
            @PathVariable UUID userId,
            Authentication authentication
    ) {
        return adminService.revokePremiumSpecial(userId, authentication.getName());
    }

    @GetMapping("/premium-special")
    public PremiumSpecialSummaryResponse premiumSpecialUsers() {
        return adminService.premiumSpecialUsers();
    }

    @GetMapping("/users/{userId}")
    public AdminUserResponse user(@PathVariable UUID userId) {
        return adminService.user(userId);
    }

    @GetMapping("/users")
    public AdminUserResponse userByEmail(@RequestParam String email) {
        return adminService.userByEmail(email);
    }
}
