package com.waypoint.backend.config.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "microsoft")
public record MicrosoftOAuthProperties(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]+", message = "contains unsupported characters") String tenant,
        @NotBlank @Pattern(regexp = "https?://.+", message = "must be an absolute HTTP or HTTPS URL") String callbackUrl,
        @NotBlank @Pattern(regexp = "https://.+", message = "must be an absolute HTTPS URL") String graphUserUrl,
        @NotBlank String tokenEncryptionKey,
        @NotEmpty List<@NotBlank String> allowedExtensionRedirectUris,
        @Min(60) long transactionTtlSeconds,
        @Min(30) long exchangeCodeTtlSeconds
) {
    private static final List<String> SCOPES = List.of(
            "openid",
            "profile",
            "email",
            "offline_access",
            "User.Read"
    );

    public MicrosoftOAuthProperties {
        clientId = trim(clientId);
        clientSecret = trim(clientSecret);
        tenant = trim(tenant);
        callbackUrl = trim(callbackUrl);
        graphUserUrl = trim(graphUserUrl);
        tokenEncryptionKey = trim(tokenEncryptionKey);
        allowedExtensionRedirectUris = allowedExtensionRedirectUris == null
                ? List.of()
                : allowedExtensionRedirectUris.stream().map(MicrosoftOAuthProperties::trim).toList();
    }

    public String authorizationUrl() {
        return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize";
    }

    public String tokenUrl() {
        return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token";
    }

    public List<String> scopes() {
        return SCOPES;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
