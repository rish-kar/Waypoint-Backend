package com.waypoint.backend.controller.admin;

import com.waypoint.backend.model.admin.AdminAccountCreateRequest;
import com.waypoint.backend.model.admin.AdminAccountResponse;
import com.waypoint.backend.model.admin.AdminAccountUpdateRequest;
import com.waypoint.backend.service.admin.AdminAccountService;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {
    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    public List<AdminAccountResponse> accounts() {
        return adminAccountService.list();
    }

    @PostMapping
    public AdminAccountResponse create(
            @Valid @RequestBody AdminAccountCreateRequest request,
            Authentication authentication
    ) {
        return adminAccountService.create(request, authentication.getName());
    }

    @PatchMapping("/{accountId}")
    public AdminAccountResponse update(
            @PathVariable UUID accountId,
            @Valid @RequestBody AdminAccountUpdateRequest request,
            Authentication authentication
    ) {
        return adminAccountService.update(accountId, request, authentication.getName());
    }
}