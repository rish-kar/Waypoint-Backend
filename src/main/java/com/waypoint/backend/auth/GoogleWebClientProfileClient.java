package com.waypoint.backend.auth;

import com.waypoint.backend.common.UnauthorizedException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class GoogleWebClientProfileClient implements GoogleProfileClient {
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
        try {
            JsonNode tokenInfo = webClient.get()
                    .uri(properties.tokenInfoUrl() + "?access_token={accessToken}", accessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            JsonNode userInfo = webClient.get()
                    .uri(properties.userInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
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

            String providerUserId = firstText(userInfo, tokenInfo, "sub");
            String email = firstText(userInfo, tokenInfo, "email");
            boolean emailVerified = bool(userInfo, "email_verified") || bool(tokenInfo, "email_verified");
            return new GoogleProfile(
                    providerUserId,
                    email,
                    emailVerified,
                    text(userInfo, "name"),
                    text(userInfo, "picture"),
                    audience
            );
        } catch (WebClientResponseException exception) {
            throw new UnauthorizedException("Invalid Google access token");
        }
    }

    private String firstText(JsonNode first, JsonNode second, String field) {
        String value = text(first, field);
        return value == null ? text(second, field) : value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
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
