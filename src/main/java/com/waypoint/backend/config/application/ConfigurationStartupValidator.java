package com.waypoint.backend.config.application;

import com.waypoint.backend.config.admin.AdminProperties;
import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.config.auth.MicrosoftOAuthProperties;
import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.security.jwt.JwtProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

@Component
public class ConfigurationStartupValidator implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationStartupValidator.class);

    private final Environment environment;
    private final AdminProperties adminProperties;
    private final AppProperties appProperties;
    private final CorsProperties corsProperties;
    private final GoogleProperties googleProperties;
    private final MicrosoftOAuthProperties microsoftProperties;
    private final JwtProperties jwtProperties;
    private final LemonSqueezyProperties lemonSqueezyProperties;

    public ConfigurationStartupValidator(
            Environment environment,
            AdminProperties adminProperties,
            AppProperties appProperties,
            CorsProperties corsProperties,
            GoogleProperties googleProperties,
            MicrosoftOAuthProperties microsoftProperties,
            JwtProperties jwtProperties,
            LemonSqueezyProperties lemonSqueezyProperties
    ) {
        this.environment = environment;
        this.adminProperties = adminProperties;
        this.appProperties = appProperties;
        this.corsProperties = corsProperties;
        this.googleProperties = googleProperties;
        this.microsoftProperties = microsoftProperties;
        this.jwtProperties = jwtProperties;
        this.lemonSqueezyProperties = lemonSqueezyProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            validateProductionConfiguration();
        }

        LOGGER.atInfo()
                .addKeyValue("event", "application_configuration_validated")
                .addKeyValue("profiles", activeProfiles())
                .addKeyValue("base_url", appProperties.baseUrl())
                .addKeyValue("cors_origin_count", corsProperties.allowedOrigins().size())
                .addKeyValue("jwt_expiration_seconds", jwtProperties.expirationSeconds())
                .addKeyValue("google_client_id", googleProperties.clientId())
                .addKeyValue("microsoft_tenant", microsoftProperties.tenant())
                .addKeyValue("microsoft_redirect_count", microsoftProperties.allowedExtensionRedirectUris().size())
                .log("Application configuration validated");
    }

    private void validateProductionConfiguration() {
        requireHttps("APP_BASE_URL", appProperties.baseUrl());
        requireHttps("GOOGLE_TOKEN_INFO_URL", googleProperties.tokenInfoUrl());
        requireHttps("GOOGLE_USER_INFO_URL", googleProperties.userInfoUrl());
        requireHttps("MICROSOFT_CALLBACK_URL", microsoftProperties.callbackUrl());
        requireHttps("MICROSOFT_GRAPH_USER_URL", microsoftProperties.graphUserUrl());
        requireHttps("LEMON_SQUEEZY_API_BASE_URL", lemonSqueezyProperties.apiBaseUrl());
        requireRedisUrl(environment.getProperty("spring.data.redis.url"));
        if (!environment.getProperty("security.distributed-state-enabled", Boolean.class, false)) {
            throw new IllegalStateException("Distributed security state must be enabled in production");
        }
        rejectPlaceholder("ADMIN_ID", adminProperties.id());
        rejectPlaceholder("ADMIN_PASSWORD", adminProperties.password());
        rejectPlaceholder("ADMIN_TOTP_SECRET", adminProperties.totpSecret());
        rejectPlaceholder("ADMIN_TOTP_ENCRYPTION_KEY", adminProperties.totpEncryptionKey());
        rejectPlaceholder("JWT_SECRET", jwtProperties.secret());
        rejectPlaceholder("GOOGLE_CLIENT_ID", googleProperties.clientId());
        rejectPlaceholder("MICROSOFT_CLIENT_ID", microsoftProperties.clientId());
        rejectPlaceholder("MICROSOFT_CLIENT_SECRET", microsoftProperties.clientSecret());
        validateMicrosoftEncryptionKey();
        validateMicrosoftRedirectUris();
        rejectPlaceholder("LEMON_SQUEEZY_API_KEY", lemonSqueezyProperties.apiKey());
        rejectPlaceholder("LEMON_SQUEEZY_STORE_ID", lemonSqueezyProperties.storeId());
        rejectPlaceholder("LEMON_SQUEEZY_MONTHLY_VARIANT_ID", lemonSqueezyProperties.monthlyVariantId());
        rejectPlaceholder("LEMON_SQUEEZY_ANNUAL_VARIANT_ID", lemonSqueezyProperties.annualVariantId());
        rejectPlaceholder("LEMON_SQUEEZY_WEBHOOK_SECRET", lemonSqueezyProperties.webhookSecret());

        boolean unsafeOrigin = corsProperties.allowedOrigins().stream().anyMatch(origin ->
                "*".equals(origin)
                        || origin.startsWith("http://")
                        || origin.contains("localhost")
                        || (!origin.startsWith("https://") && !origin.startsWith("chrome-extension://"))
        );
        if (unsafeOrigin) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must contain only explicit HTTPS or chrome-extension origins in production"
            );
        }

        LOGGER.atInfo()
                .addKeyValue("event", "production_configuration_validated")
                .addKeyValue("cors_origin_count", corsProperties.allowedOrigins().size())
                .log("Production configuration validated");
    }

    private void validateMicrosoftRedirectUris() {
        if (microsoftProperties.allowedExtensionRedirectUris().isEmpty()) {
            throw new IllegalStateException("MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS must not be empty");
        }
        for (String redirectUri : microsoftProperties.allowedExtensionRedirectUris()) {
            URI uri;
            try {
                uri = URI.create(redirectUri);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS contains an invalid URI",
                        exception
                );
            }
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || (!host.equals("chromiumapp.org") && !host.endsWith(".chromiumapp.org"))
                    || uri.getFragment() != null) {
                throw new IllegalStateException(
                        "MICROSOFT_ALLOWED_EXTENSION_REDIRECT_URIS must contain only explicit HTTPS chromiumapp.org URIs"
                );
            }
        }
    }

    private void validateMicrosoftEncryptionKey() {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(microsoftProperties.tokenEncryptionKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MICROSOFT_TOKEN_ENCRYPTION_KEY must be Base64 encoded", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("MICROSOFT_TOKEN_ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
    }

    private void requireHttps(String variableName, String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(variableName + " must be a valid absolute HTTPS URL", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException(variableName + " must be a valid absolute HTTPS URL");
        }
    }

    private void requireRedisUrl(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("REDIS_URL must be configured in production");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("REDIS_URL must be a valid redis:// or rediss:// URL", exception);
        }
        if ((!("redis".equalsIgnoreCase(uri.getScheme()) || "rediss".equalsIgnoreCase(uri.getScheme())))
                || uri.getHost() == null) {
            throw new IllegalStateException("REDIS_URL must be a valid redis:// or rediss:// URL");
        }
    }

    private void rejectPlaceholder(String variableName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(variableName + " must be configured in production");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("local-")
                || normalized.startsWith("test-")
                || normalized.contains("placeholder")
                || normalized.contains("replace")
                || normalized.contains("change-me")
                || normalized.contains("development")
                || normalized.contains("change-before-production")) {
            throw new IllegalStateException(variableName + " contains a development placeholder");
        }
    }

    private String activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return String.join(",", Arrays.stream(profiles).sorted().toList());
    }
}
