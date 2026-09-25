package com.waypoint.backend.controller.admin;

import com.waypoint.backend.model.admin.AdminMetricsResponse;
import com.waypoint.backend.service.admin.AdminMetricsService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminMetricsController {
    private final AdminMetricsService adminMetricsService;

    public AdminMetricsController(AdminMetricsService adminMetricsService) {
        this.adminMetricsService = adminMetricsService;
    }

    @GetMapping("/metrics")
    public AdminMetricsResponse metrics() {
        return adminMetricsService.snapshot();
    }
}
