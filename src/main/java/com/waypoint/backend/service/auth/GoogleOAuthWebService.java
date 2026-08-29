package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.application.AppProperties;
import com.waypoint.backend.config.auth.GoogleOAuthProperties;
import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.GoogleOAuthStartResponse;
import com.waypoint.backend.model.auth.GoogleOAuthStatusResponse;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleOAuthWebService {
    private static final Duration TRANSACTION_TTL = Duration.ofMinutes(5);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETE = "COMPLETE";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_EXPIRED = "EXPIRED";

    private final GoogleProperties googleProperties;
    private final GoogleOAuthProperties oauthProperties;
    private final AppProperties appProperties;
    private final GoogleAuthService googleAuthService;
    private final RestClient restClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingLogin> pendingLogins = new ConcurrentHashMap<>();
    private final Map<String, String> stateToTransaction = new ConcurrentHashMap<>();

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

    public GoogleOAuthStartResponse start() {
        requireConfigured();
        removeExpiredTransactions();

        String transactionId = randomToken(32);
        String state = randomToken(32);
        String codeVerifier = randomToken(48);
        PendingLogin pending = new PendingLogin(state, codeVerifier, Instant.now().plus(TRANSACTION_TTL));
        pendingLogins.put(transactionId, pending);
        stateToTransaction.put(state, transactionId);

        URI authorizationUri = UriComponentsBuilder.fromUriString(oauthProperties.authorizationUrl())
                .queryParam("client_id", googleProperties.clientId())
                .queryParam("redirect_uri", callbackUrl())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("prompt", "select_account")
                .build()
                .encode()
                .toUri();

        return new GoogleOAuthStartResponse(
                transactionId,
                authorizationUri.toString(),
                TRANSACTION_TTL.toSeconds()
        );
    }

    public GoogleOAuthStatusResponse status(String transactionId) {
        removeExpiredTransactions();
        String safeTransactionId = normalizeTransactionId(transactionId);
        PendingLogin pending = pendingLogins.get(safeTransactionId);
        if (pending == null || isExpired(pending)) {
            removeTransaction(safeTransactionId, pending);
            return new GoogleOAuthStatusResponse(STATUS_EXPIRED, null);
        }
        if (StringUtils.hasText(pending.errorCode)) {
            return new GoogleOAuthStatusResponse(STATUS_FAILED, pending.errorCode);
        }
        if (pending.authResponse != null) {
            return new GoogleOAuthStatusResponse(STATUS_COMPLETE, null);
        }
        return new GoogleOAuthStatusResponse(STATUS_PENDING, null);
    }

    public AuthResponse exchange(String transactionId) {
        String safeTransactionId = normalizeTransactionId(transactionId);
        PendingLogin pending = pendingLogins.get(safeTransactionId);
        if (pending == null || isExpired(pending)) {
            removeTransaction(safeTransactionId, pending);
            throw new UnauthorizedException("Google sign-in transaction is invalid or expired");
        }

        synchronized (pending) {
            if (StringUtils.hasText(pending.errorCode)) {
                removeTransaction(safeTransactionId, pending);
                throw new UnauthorizedException("Google sign-in failed");
            }
            if (pending.authResponse == null) {
                throw new InvalidRequestException("Google sign-in is still pending");
            }
            AuthResponse response = pending.authResponse;
            if (!pendingLogins.remove(safeTransactionId, pending)) {
                throw new UnauthorizedException("Google sign-in transaction has already been exchanged");
            }
            stateToTransaction.remove(pending.state, safeTransactionId);
            pending.authResponse = null;
            return response;
        }
    }

    public CallbackPage callbackPage(String code, String state, String providerError) {
        if (!StringUtils.hasText(state)) {
            return new CallbackPage(false);
        }

        String transactionId = stateToTransaction.remove(state);
        PendingLogin pending = transactionId == null ? null : pendingLogins.get(transactionId);
        if (pending == null || isExpired(pending) || !pending.state.equals(state)) {
            removeTransaction(transactionId, pending);
            return new CallbackPage(false);
        }

        if (StringUtils.hasText(providerError)) {
            pending.errorCode = "provider_error";
            return new CallbackPage(false);
        }
        if (!StringUtils.hasText(code)) {
            pending.errorCode = "missing_code";
            return new CallbackPage(false);
        }

        try {
            String googleAccessToken = exchangeCode(code, pending.codeVerifier);
            pending.authResponse = googleAuthService.login(googleAccessToken);
            return new CallbackPage(true);
        } catch (RuntimeException exception) {
            pending.errorCode = "authentication_failed";
            return new CallbackPage(false);
        }
    }

    private String exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", googleProperties.clientId());
        form.add("client_secret", oauthProperties.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", callbackUrl());
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", codeVerifier);

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

    private String normalizeTransactionId(String value) {
        String transactionId = String.valueOf(value == null ? "" : value).trim();
        if (!transactionId.matches("[A-Za-z0-9_-]{32,128}")) return "";
        return transactionId;
    }

    private boolean isExpired(PendingLogin pending) {
        return pending == null || !pending.expiresAt.isAfter(Instant.now());
    }

    private void removeExpiredTransactions() {
        Instant now = Instant.now();
        pendingLogins.forEach((transactionId, pending) -> {
            if (!pending.expiresAt.isAfter(now)) removeTransaction(transactionId, pending);
        });
    }

    private void removeTransaction(String transactionId, PendingLogin pending) {
        if (pending == null) return;
        String safeTransactionId = normalizeTransactionId(transactionId);
        pendingLogins.remove(safeTransactionId, pending);
        stateToTransaction.remove(pending.state, safeTransactionId);
    }

    private String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String codeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CallbackPage(boolean success) {
    }

    private static final class PendingLogin {
        private final String state;
        private final String codeVerifier;
        private final Instant expiresAt;
        private volatile AuthResponse authResponse;
        private volatile String errorCode;

        private PendingLogin(String state, String codeVerifier, Instant expiresAt) {
            this.state = state;
            this.codeVerifier = codeVerifier;
            this.expiresAt = expiresAt;
        }
    }
}
