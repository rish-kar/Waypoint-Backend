package com.waypoint.backend.utilities.client.google;

import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.utilities.exception.UnauthorizedException;
import com.waypoint.backend.utilities.exception.UpstreamServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;

@Component
public class GoogleWebClientProfileClient implements GoogleProfileClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleWebClientProfileClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final GoogleProperties properties;

    public GoogleWebClientProfileClient(
            WebClient.Builder webClientBuilder,
            GoogleProperties properties
    ) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public GoogleProfile fetchProfile(String accessToken) {
        String normalizedAccessToken = accessToken == null ? null : accessToken.trim();
        if (!StringUtils.hasText(normalizedAccessToken)) {
            throw rejected("blank_token", "Invalid Google access token");
        }

        try {
            JsonNode tokenInfo = fetchTokenInfo(normalizedAccessToken);
            JsonNode userInfo = fetchUserInfo(normalizedAccessToken);

            if (tokenInfo == null) {
                throw rejected("missing_tokeninfo", "Invalid Google access token");
            }
            if (userInfo == null) {
                throw rejected("missing_userinfo", "Invalid Google access token");
            }

            String tokenClientId = firstText(tokenInfo, "issued_to", "audience", "azp", "aud");
            if (!StringUtils.hasText(tokenClientId)) {
                throw rejected("missing_audience", "Google access token audience is missing");
            }

            String configuredClientId = properties.clientId().trim();
            if (!configuredClientId.equals(tokenClientId.trim())) {
                throw rejectedAudience(configuredClientId, tokenClientId.trim());
            }

            long expiresInSeconds = longValue(tokenInfo, "expires_in");
            if (expiresInSeconds <= 0) {
                throw rejected("expired_token", "Google access token has expired");
            }

            String tokenSubject = firstText(tokenInfo, "sub", "user_id");
            String userSubject = text(userInfo, "sub");
            if (!StringUtils.hasText(tokenSubject) && !StringUtils.hasText(userSubject)) {
                throw rejected("missing_subject", "Google account is missing a provider user ID");
            }
            if (StringUtils.hasText(tokenSubject)
                    && StringUtils.hasText(userSubject)
                    && !tokenSubject.equals(userSubject)) {
                throw rejected("subject_mismatch", "Google account identity is inconsistent");
            }

            String tokenEmail = text(tokenInfo, "email");
            String userEmail = text(userInfo, "email");
            if (StringUtils.hasText(tokenEmail)
                    && StringUtils.hasText(userEmail)
                    && !tokenEmail.equalsIgnoreCase(userEmail)) {
                throw rejected("email_mismatch", "Google account email is inconsistent");
            }

            String providerUserId = StringUtils.hasText(userSubject) ? userSubject : tokenSubject;
            String email = StringUtils.hasText(userEmail) ? userEmail : tokenEmail;
            boolean emailVerified = bool(userInfo, "email_verified")
                    || bool(tokenInfo, "email_verified")
                    || bool(tokenInfo, "verified_email");

            return new GoogleProfile(
                    providerUserId,
                    email,
                    emailVerified,
                    text(userInfo, "name"),
                    text(userInfo, "picture"),
                    tokenClientId.trim(),
                    expiresInSeconds
            );
        } catch (UnauthorizedException | UpstreamServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new UpstreamServiceException("Google authentication service is unavailable");
        }
    }

    private JsonNode fetchTokenInfo(String accessToken) {
        try {
            return webClient.get()
                    .uri(properties.tokenInfoUrl() + "?access_token={accessToken}", accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw rejected("tokeninfo_rejected", "Invalid Google access token");
            }
            throw new UpstreamServiceException("Google authentication service is unavailable");
        } catch (WebClientRequestException exception) {
            throw new UpstreamServiceException("Google authentication service is unavailable");
        }
    }

    private JsonNode fetchUserInfo(String accessToken) {
        try {
            return webClient.get()
                    .uri(properties.userInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw rejected("userinfo_rejected", "Invalid Google access token");
            }
            throw new UpstreamServiceException("Google authentication service is unavailable");
        } catch (WebClientRequestException exception) {
            throw new UpstreamServiceException("Google authentication service is unavailable");
        }
    }

    private UnauthorizedException rejectedAudience(String configuredClientId, String tokenClientId) {
        LOGGER.atWarn()
                .addKeyValue("event", "google_auth_validation_rejected")
                .addKeyValue("reason", "invalid_audience")
                .addKeyValue("configured_client_id", configuredClientId)
                .addKeyValue("token_client_id", tokenClientId)
                .log("Google authentication validation rejected");
        return new UnauthorizedException("Google access token audience is invalid");
    }

    private UnauthorizedException rejected(String reason, String message) {
        LOGGER.atWarn()
                .addKeyValue("event", "google_auth_validation_rejected")
                .addKeyValue("reason", reason)
                .log("Google authentication validation rejected");
        return new UnauthorizedException(message);
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return -1;
        }
        return value.asLong(-1);
    }

    private boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return "true".equalsIgnoreCase(value.asText());
    }
}
