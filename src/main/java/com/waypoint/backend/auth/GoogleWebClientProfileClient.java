package com.waypoint.backend.auth;

import com.waypoint.backend.common.UnauthorizedException;
import com.waypoint.backend.common.UpstreamServiceException;
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
        if (!StringUtils.hasText(accessToken)) {
            throw new UnauthorizedException("Invalid Google access token");
        }

        try {
            JsonNode tokenInfo = webClient.get()
                    .uri(properties.tokenInfoUrl() + "?access_token={accessToken}", accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            JsonNode userInfo = webClient.get()
                    .uri(properties.userInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (tokenInfo == null || userInfo == null) {
                throw new UnauthorizedException("Invalid Google access token");
            }

            String audience = text(tokenInfo, "aud");
            if (!StringUtils.hasText(audience)) {
                throw new UnauthorizedException("Google access token audience is missing");
            }
            if (!properties.clientId().equals(audience)) {
                throw new UnauthorizedException("Google access token audience is invalid");
            }

            long expiresInSeconds = longValue(tokenInfo, "expires_in");
            if (expiresInSeconds <= 0) {
                throw new UnauthorizedException("Google access token has expired");
            }

            String tokenSubject = text(tokenInfo, "sub");
            String userSubject = text(userInfo, "sub");
            if (!StringUtils.hasText(tokenSubject) && !StringUtils.hasText(userSubject)) {
                throw new UnauthorizedException("Google account is missing a provider user ID");
            }
            if (StringUtils.hasText(tokenSubject)
                    && StringUtils.hasText(userSubject)
                    && !tokenSubject.equals(userSubject)) {
                throw new UnauthorizedException("Google account identity is inconsistent");
            }

            String tokenEmail = text(tokenInfo, "email");
            String userEmail = text(userInfo, "email");
            if (StringUtils.hasText(tokenEmail)
                    && StringUtils.hasText(userEmail)
                    && !tokenEmail.equalsIgnoreCase(userEmail)) {
                throw new UnauthorizedException("Google account email is inconsistent");
            }

            String providerUserId = StringUtils.hasText(userSubject) ? userSubject : tokenSubject;
            String email = StringUtils.hasText(userEmail) ? userEmail : tokenEmail;
            boolean emailVerified = bool(userInfo, "email_verified") || bool(tokenInfo, "email_verified");

            return new GoogleProfile(
                    providerUserId,
                    email,
                    emailVerified,
                    text(userInfo, "name"),
                    text(userInfo, "picture"),
                    audience,
                    expiresInSeconds
            );
        } catch (UnauthorizedException | UpstreamServiceException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new UnauthorizedException("Invalid Google access token");
            }
            throw new UpstreamServiceException("Google authentication service is unavailable");
        } catch (WebClientRequestException exception) {
            throw new UpstreamServiceException("Google authentication service is unavailable");
        } catch (RuntimeException exception) {
            throw new UpstreamServiceException("Google authentication service is unavailable");
        }
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
