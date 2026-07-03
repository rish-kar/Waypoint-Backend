package com.waypoint.backend.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(
        @NotBlank String clientId,
        @NotBlank String tokenInfoUrl,
        @NotBlank String userInfoUrl
) {
}
