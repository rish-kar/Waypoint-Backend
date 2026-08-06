package com.waypoint.backend.config.application;

import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.security.jwt.JwtProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

@Component
public class ConfigurationStartupValidator implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationStartupValidator.class);

    private final Environment environment;
    private final AppProperties appProperties;
    private final CorsProperties corsProperties;
    private final GoogleProperties googleProperties;
    private final JwtProperties jwtProperties;
    private final LemonSqueezyProperties lemonSqueezyProperties;

    public ConfigurationStartupValidator(
            Environment environment,
            AppProperties appProperties,
            CorsProperties corsProperties,
            GoogleProperties googleProperties,
            JwtProperties jwtProperties,
            LemonSqueezyProperties lemonSqueezyProperties
    ) {
        this.environment = environment;
        this.appProperties = appProperties;
        this.corsProperties = corsProperties;
        this.googleProperties = googleProperties;
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
                .log("Application configuration validated");
    }

    private void validateProductionConfiguration() {
        requireHttps("APP_BASE_URL", appProperties.baseUrl());
        requireHttps("GOOGLE_TOKEN_INFO_URL", googleProperties.tokenInfoUrl());
        requireHttps("GOOGLE_USER_INFO_URL", googleProperties.userInfoUrl());
        requireHttps("LEMON_SQUEEZY_API_BASE_URL", lemonSqueezyProperties.apiBaseUrl());
        rejectPlaceholder("GOOGLE_CLIENT_ID", googleProperties.clientId());
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

    private void rejectPlaceholder(String variableName, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("local-")
                || normalized.startsWith("test-")
                || normalized.contains("placeholder")
                || normalized.contains("replace")
                || normalized.contains("change-me")) {
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
