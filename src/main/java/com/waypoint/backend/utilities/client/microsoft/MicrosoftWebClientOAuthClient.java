package com.waypoint.backend.utilities.client.microsoft;

import com.waypoint.backend.config.auth.MicrosoftOAuthProperties;
import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.auth.MicrosoftTokenSet;
import com.waypoint.backend.utilities.exception.UnauthorizedException;
import com.waypoint.backend.utilities.exception.UpstreamServiceException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;

@Component
public class MicrosoftWebClientOAuthClient implements MicrosoftOAuthClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final WebClient webClient;
    private final MicrosoftOAuthProperties properties;

    public MicrosoftWebClientOAuthClient(WebClient.Builder webClientBuilder, MicrosoftOAuthProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public MicrosoftTokenSet exchangeAuthorizationCode(String authorizationCode, String codeVerifier) {
        if (!StringUtils.hasText(authorizationCode) || !StringUtils.hasText(codeVerifier)) {
            throw new UnauthorizedException("Microsoft authentication failed");
        }
        MultiValueMap<String, String> form = baseTokenForm();
        form.add("code", authorizationCode.trim());
        form.add("redirect_uri", properties.callbackUrl());
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", codeVerifier);
        return parseTokenSet(postToken(form));
    }

    @Override
    public MicrosoftTokenSet refreshAccessToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) throw new UnauthorizedException("Microsoft credential is unavailable");
        MultiValueMap<String, String> form = baseTokenForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return parseTokenSet(postToken(form));
    }

    @Override
    public MicrosoftProfile fetchProfile(String accessToken) {
        if (!StringUtils.hasText(accessToken)) throw new UnauthorizedException("Microsoft authentication failed");
        try {
            JsonNode payload = webClient.get()
                    .uri(properties.graphUserUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
            String providerUserId = text(payload, "id");
            String email = firstText(payload, "mail", "userPrincipalName");
            String displayName = text(payload, "displayName");
            if (!StringUtils.hasText(providerUserId) || !StringUtils.hasText(email) || !email.contains("@")) {
                throw new UnauthorizedException("Microsoft account does not provide a usable email address");
            }
            return new MicrosoftProfile(providerUserId.trim(), email.trim(), displayName);
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new UnauthorizedException("Microsoft authentication failed");
            throw new UpstreamServiceException("Microsoft authentication service is unavailable");
        } catch (WebClientRequestException exception) {
            throw new UpstreamServiceException("Microsoft authentication service is unavailable");
        } catch (RuntimeException exception) {
            throw new UpstreamServiceException("Microsoft authentication service is unavailable");
        }
    }

    private MultiValueMap<String, String> baseTokenForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("scope", String.join(" ", properties.scopes()));
        return form;
    }

    private MicrosoftTokenSet parseTokenSet(JsonNode payload) {
        String accessToken = text(payload, "access_token");
        String refreshToken = text(payload, "refresh_token");
        long expiresIn = longValue(payload, "expires_in");
        String scopes = text(payload, "scope");
        if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(refreshToken) || expiresIn <= 0) {
            throw new UnauthorizedException("Microsoft authentication failed");
        }
        return new MicrosoftTokenSet(accessToken, refreshToken, expiresIn, scopes);
    }

    private JsonNode postToken(MultiValueMap<String, String> form) {
        try {
            return webClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new UnauthorizedException("Microsoft authentication failed");
            throw new UpstreamServiceException("Microsoft authentication service is unavailable");
        } catch (WebClientRequestException exception) {
            throw new UpstreamServiceException("Microsoft authentication service is unavailable");
        } catch (RuntimeException exception) {
            throw new UpstreamServiceException("Microsoft authentication service is unavailable");
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private long longValue(JsonNode node, String field) {
        if (node == null) return -1;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? -1 : value.asLong(-1);
    }
}
