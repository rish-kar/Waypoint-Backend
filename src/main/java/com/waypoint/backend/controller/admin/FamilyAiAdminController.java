package com.waypoint.backend.controller.admin;

import com.waypoint.backend.model.admin.AdminFamilyAiUsageResponse;
import com.waypoint.backend.service.admin.FamilyAiAdminService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/family-ai")
public class FamilyAiAdminController {
    private final FamilyAiAdminService familyAiAdminService;

    public FamilyAiAdminController(FamilyAiAdminService familyAiAdminService) {
        this.familyAiAdminService = familyAiAdminService;
    }

    @GetMapping
    public AdminFamilyAiUsageResponse current() {
        return familyAiAdminService.currentForAdminApi();
    }
}
