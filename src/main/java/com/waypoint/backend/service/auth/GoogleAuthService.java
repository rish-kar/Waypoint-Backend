package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.service.user.UserService;
import com.waypoint.backend.utilities.client.google.GoogleProfileClient;
import com.waypoint.backend.utilities.exception.UnauthorizedException;
import com.waypoint.backend.utilities.exception.UpstreamServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GoogleAuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleAuthService.class);

    private final GoogleProfileClient googleProfileClient;
    private final UserService userService;
    private final WaypointSessionService sessionService;
    private final GoogleProperties googleProperties;

    public GoogleAuthService(GoogleProfileClient googleProfileClient,
                             UserService userService,
                             WaypointSessionService sessionService,
                             GoogleProperties googleProperties) {
        this.googleProfileClient = googleProfileClient;
        this.userService = userService;
        this.sessionService = sessionService;
        this.googleProperties = googleProperties;
    }

    public AuthResponse login(String googleAccessToken) {
        GoogleProfile profile = fetchProfile(googleAccessToken);
        validateProfile(profile);
        UserEntity user = userService.findOrCreateGoogleUser(profile);

        LOGGER.atInfo()
                .addKeyValue("event", "authentication_succeeded")
                .addKeyValue("provider", "google")
                .addKeyValue("user_id", user.getId())
                .log("User authenticated");

        return sessionService.issue(user.getId());
    }

    private GoogleProfile fetchProfile(String googleAccessToken) {
        try {
            return googleProfileClient.fetchProfile(googleAccessToken);
        } catch (UnauthorizedException exception) {
            logRejected("provider_rejected_token");
            throw new UnauthorizedException("Google authentication failed");
        } catch (UpstreamServiceException exception) {
            LOGGER.atError().addKeyValue("event", "authentication_provider_unavailable")
                    .addKeyValue("provider", "google").log("Google authentication provider is unavailable");
            throw exception;
        }
    }

    private void validateProfile(GoogleProfile profile) {
        if (profile == null) reject("missing_profile");
        if (!StringUtils.hasText(profile.providerUserId())) reject("missing_provider_user_id");
        if (!StringUtils.hasText(profile.email()) || !profile.emailVerified()) reject("unverified_email");
        if (!StringUtils.hasText(profile.audience()) || !googleProperties.clientId().equals(profile.audience())) reject("invalid_audience");
        if (profile.expiresInSeconds() <= 0) reject("expired_provider_token");
    }

    private void reject(String reason) {
        logRejected(reason);
        throw new UnauthorizedException("Google authentication failed");
    }

    private void logRejected(String reason) {
        LOGGER.atWarn().addKeyValue("event", "authentication_rejected")
                .addKeyValue("provider", "google").addKeyValue("reason", reason).log("Google authentication rejected");
    }
}
