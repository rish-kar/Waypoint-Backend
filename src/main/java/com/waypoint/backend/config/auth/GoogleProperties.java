package com.waypoint.backend.config.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(
        @NotBlank String clientId,
        @NotBlank @Pattern(regexp = "https?://.+", message = "must be an absolute HTTP or HTTPS URL") String tokenInfoUrl,
        @NotBlank @Pattern(regexp = "https?://.+", message = "must be an absolute HTTP or HTTPS URL") String userInfoUrl
) {
}
