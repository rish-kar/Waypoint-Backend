package com.waypoint.backend.config.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        @NotBlank @Size(max = 100) String id,
        @NotBlank @Size(min = 8, max = 200) String password,
        String totpSecret,
        @NotBlank @Size(min = 32, max = 500) String totpEncryptionKey
) {
}
