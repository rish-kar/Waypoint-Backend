package com.waypoint.backend.config.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "must be an absolute HTTP or HTTPS URL")
        String baseUrl
) {
}
