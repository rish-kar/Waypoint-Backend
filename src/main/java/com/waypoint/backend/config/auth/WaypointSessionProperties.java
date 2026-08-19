package com.waypoint.backend.config.auth;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "waypoint-session")
public record WaypointSessionProperties(
        @Min(3600) long refreshTokenTtlSeconds,
        @Min(60000) long cleanupMs
) {
}
