package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.auth.MicrosoftOAuthProperties;
import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.MicrosoftAuthStartResponse;
import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.auth.MicrosoftTokenSet;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.utilities.client.microsoft.MicrosoftOAuthClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class MicrosoftOAuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicrosoftOAuthService.class);

    private final MicrosoftOAuthProperties properties;
    private final MicrosoftOAuthStateService stateService;
    private final MicrosoftExchangeCodeService exchangeCodeService;
    private final MicrosoftAccountService accountService;
    private final MicrosoftOAuthClient microsoftOAuthClient;
    private final WaypointSessionService sessionService;
    private final Set<String> allowedRedirectUris;

    public MicrosoftOAuthService(MicrosoftOAuthProperties properties,
                                 MicrosoftOAuthStateService stateService,
                                 MicrosoftExchangeCodeService exchangeCodeService,
                                 MicrosoftAccountService accountService,
                                 MicrosoftOAuthClient microsoftOAuthClient,
                                 WaypointSessionService sessionService) {
        this.properties = properties;
        this.stateService = stateService;
        this.exchangeCodeService = exchangeCodeService;
        this.accountService = accountService;
        this.microsoftOAuthClient = microsoftOAuthClient;
        this.sessionService = sessionService;
        this.allowedRedirectUris = Set.copyOf(properties.allowedExtensionRedirectUris());
    }

    public MicrosoftAuthStartResponse start(String redirectUri) {
        return startInternal(redirectUri, null);
    }

    public MicrosoftAuthStartResponse startLink(String redirectUri, UUID userId) {
        if (userId == null) throw new UnauthorizedException("Authentication required");
        return startInternal(redirectUri, userId);
    }

    private MicrosoftAuthStartResponse startInternal(String redirectUri, UUID linkUserId) {
        String normalizedRedirectUri = normalizeAllowedRedirectUri(redirectUri);
        MicrosoftOAuthStateService.PendingAuthorization pending = stateService.create(normalizedRedirectUri, linkUserId);
        String authorizationUrl = UriComponentsBuilder.fromUriString(properties.authorizationUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", properties.callbackUrl())
                .queryParam("response_mode", "query")
                .queryParam("scope", String.join(" ", properties.scopes()))
                .queryParam("state", pending.state())
                .queryParam("code_challenge", pending.codeChallenge())
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUriString();
        long expiresIn = Math.max(1, Duration.between(Instant.now(), pending.expiresAt()).toSeconds());
        LOGGER.atInfo().addKeyValue("event", linkUserId == null ? "microsoft_oauth_started" : "microsoft_link_started")
                .addKeyValue("transaction_id", pending.transactionId()).log("Microsoft OAuth transaction started");
        return new MicrosoftAuthStartResponse(authorizationUrl, pending.transactionId(), expiresIn);
    }

    public URI callback(String code, String state, String providerError) {
        MicrosoftOAuthStateService.ConsumedAuthorization pending = stateService.consume(state);
        if (StringUtils.hasText(providerError)) {
            LOGGER.atWarn().addKeyValue("event", "microsoft_oauth_rejected")
                    .addKeyValue("transaction_id", pending.transactionId())
                    .addKeyValue("reason", "provider_denied").log("Microsoft OAuth authorization was denied");
            return failureRedirect(pending.extensionRedirectUri(), "authorization_denied");
        }
        if (!StringUtils.hasText(code)) return failureRedirect(pending.extensionRedirectUri(), "missing_authorization_code");
        try {
            MicrosoftTokenSet tokens = microsoftOAuthClient.exchangeAuthorizationCode(code, pending.codeVerifier());
            MicrosoftProfile profile = microsoftOAuthClient.fetchProfile(tokens.accessToken());
            UserEntity user = accountService.link(profile, tokens, pending.linkUserId());
            String exchangeCode = exchangeCodeService.issue(user.getId());
            LOGGER.atInfo().addKeyValue("event", "authentication_succeeded").addKeyValue("provider", "microsoft")
                    .addKeyValue("user_id", user.getId()).addKeyValue("transaction_id", pending.transactionId())
                    .log("User authenticated");
            return successRedirect(pending.extensionRedirectUri(), exchangeCode);
        } catch (RuntimeException exception) {
            LOGGER.atWarn().addKeyValue("event", "microsoft_oauth_failed")
                    .addKeyValue("transaction_id", pending.transactionId())
                    .addKeyValue("exception_type", exception.getClass().getSimpleName())
                    .log("Microsoft OAuth transaction failed");
            return failureRedirect(pending.extensionRedirectUri(), "authentication_failed");
        }
    }

    public AuthResponse exchange(String rawExchangeCode) {
        UUID userId = exchangeCodeService.consume(rawExchangeCode);
        return sessionService.issue(userId);
    }

    private String normalizeAllowedRedirectUri(String redirectUri) {
        if (!StringUtils.hasText(redirectUri)) throw new InvalidRequestException("Microsoft redirect URI is required");
        String normalized = redirectUri.trim();
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Microsoft redirect URI is invalid");
        }
        if (!uri.isAbsolute() || uri.getFragment() != null || !allowedRedirectUris.contains(normalized)) {
            throw new InvalidRequestException("Microsoft redirect URI is not allowlisted");
        }
        return normalized;
    }

    private URI successRedirect(String redirectUri, String exchangeCode) {
        return UriComponentsBuilder.fromUriString(redirectUri).queryParam("waypoint_auth", "success")
                .queryParam("exchange_code", exchangeCode).build().encode().toUri();
    }

    private URI failureRedirect(String redirectUri, String errorCode) {
        return UriComponentsBuilder.fromUriString(redirectUri).queryParam("waypoint_auth", "error")
                .queryParam("error", errorCode).build().encode().toUri();
    }
}
