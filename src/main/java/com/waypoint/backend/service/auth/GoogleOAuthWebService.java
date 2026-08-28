package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.application.AppProperties;
import com.waypoint.backend.config.auth.GoogleOAuthProperties;
import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.utilities.exception.UnauthorizedException;
import com.waypoint.backend.utilities.exception.UpstreamServiceException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleOAuthWebService {
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final GoogleProperties googleProperties;
    private final GoogleOAuthProperties oauthProperties;
    private final AppProperties appProperties;
    private final GoogleAuthService googleAuthService;
    private final RestClient restClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingLogin> pendingLogins = new ConcurrentHashMap<>();

    public GoogleOAuthWebService(
            GoogleProperties googleProperties,
            GoogleOAuthProperties oauthProperties,
            AppProperties appProperties,
            GoogleAuthService googleAuthService
    ) {
        this.googleProperties = googleProperties;
        this.oauthProperties = oauthProperties;
        this.appProperties = appProperties;
        this.googleAuthService = googleAuthService;
        this.restClient = RestClient.builder().build();
    }

    public URI authorizationUri(String returnUrl) {
        requireConfigured();
        String safeReturnUrl = validateReturnUrl(returnUrl);
        removeExpiredStates();

        String state = randomState();
        pendingLogins.put(state, new PendingLogin(safeReturnUrl, Instant.now().plus(STATE_TTL)));

        return UriComponentsBuilder.fromUriString(oauthProperties.authorizationUrl())
                .queryParam("client_id", googleProperties.clientId())
                .queryParam("redirect_uri", callbackUrl())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("prompt", "select_account")
                .build()
                .encode()
                .toUri();
    }

    public URI callbackUri(String code, String state, String providerError) {
        PendingLogin pending = consumeState(state);
        if (pending == null) {
            throw new UnauthorizedException("Google sign-in state is invalid or expired");
        }

        if (StringUtils.hasText(providerError)) {
            return errorRedirect(pending.returnUrl(), providerError);
        }
        if (!StringUtils.hasText(code)) {
            return errorRedirect(pending.returnUrl(), "missing_code");
        }

        try {
            String googleAccessToken = exchangeCode(code);
            AuthResponse authResponse = googleAuthService.login(googleAccessToken);
            return successRedirect(pending.returnUrl(), authResponse);
        } catch (RuntimeException exception) {
            return errorRedirect(pending.returnUrl(), "authentication_failed");
        }
    }

    private String exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", googleProperties.clientId());
        form.add("client_secret", oauthProperties.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", callbackUrl());
        form.add("grant_type", "authorization_code");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(oauthProperties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            String accessToken = response == null ? "" : String.valueOf(response.getOrDefault("access_token", ""));
            if (!StringUtils.hasText(accessToken)) {
                throw new UnauthorizedException("Google authentication failed");
            }
            return accessToken;
        } catch (RestClientException exception) {
            throw new UpstreamServiceException("Google authentication provider is unavailable", exception);
        }
    }

    private URI successRedirect(String returnUrl, AuthResponse response) {
        return URI.create(returnUrl + "#access_token=" + encode(response.accessToken())
                + "&token_type=" + encode(response.tokenType())
                + "&expires_in=" + response.expiresIn());
    }

    private URI errorRedirect(String returnUrl, String error) {
        return URI.create(returnUrl + "#error=" + encode(error));
    }

    private String callbackUrl() {
        return appProperties.baseUrl().replaceAll("/+$", "") + "/api/v1/auth/google/callback";
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(googleProperties.clientId())
                || !StringUtils.hasText(oauthProperties.clientSecret())
                || !StringUtils.hasText(oauthProperties.authorizationUrl())
                || !StringUtils.hasText(oauthProperties.tokenUrl())) {
            throw new IllegalStateException("Google OAuth is not configured");
        }
    }

    private String validateReturnUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || !host.matches("[a-p]{32}\\.chromiumapp\\.org")
                    || uri.getRawUserInfo() != null
                    || uri.getPort() != -1) {
                throw new IllegalArgumentException("Invalid extension return URL");
            }
            return uri.toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid extension return URL");
        }
    }

    private PendingLogin consumeState(String state) {
        if (!StringUtils.hasText(state)) return null;
        PendingLogin pending = pendingLogins.remove(state);
        return pending != null && pending.expiresAt().isAfter(Instant.now()) ? pending : null;
    }

    private void removeExpiredStates() {
        Instant now = Instant.now();
        pendingLogins.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String randomState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(String.valueOf(value), java.nio.charset.StandardCharsets.UTF_8);
    }

    private record PendingLogin(String returnUrl, Instant expiresAt) {
    }
}
