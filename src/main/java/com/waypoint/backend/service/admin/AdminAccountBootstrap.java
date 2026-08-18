package com.waypoint.backend.service.admin;

import com.waypoint.backend.config.admin.AdminProperties;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountBootstrap implements ApplicationRunner {
    private final AdminAccountService adminAccountService;
    private final AdminProperties adminProperties;

    public AdminAccountBootstrap(AdminAccountService adminAccountService, AdminProperties adminProperties) {
        this.adminAccountService = adminAccountService;
        this.adminProperties = adminProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminAccountService.bootstrap(adminProperties);
    }
}