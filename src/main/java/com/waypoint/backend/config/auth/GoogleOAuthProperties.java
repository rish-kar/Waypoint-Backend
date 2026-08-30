package com.waypoint.backend.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google-oauth")
public record GoogleOAuthProperties(
        String clientSecret,
        String authorizationUrl,
        String tokenUrl
) {
    public GoogleOAuthProperties {
        clientSecret = clientSecret == null ? "" : clientSecret.trim();
        authorizationUrl = authorizationUrl == null ? "" : authorizationUrl.trim();
        tokenUrl = tokenUrl == null ? "" : tokenUrl.trim();
    }
}
