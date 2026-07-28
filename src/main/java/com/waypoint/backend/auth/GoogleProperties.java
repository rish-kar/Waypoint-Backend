package com.waypoint.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(
        @NotBlank String clientId,
        @NotBlank @Pattern(regexp = "https://.+") String tokenInfoUrl,
        @NotBlank @Pattern(regexp = "https://.+") String userInfoUrl
) {
}
