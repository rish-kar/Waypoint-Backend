package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.service.user.UserService;
import com.waypoint.backend.utilities.client.google.GoogleProfileClient;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GoogleAuthServiceTests {
    private GoogleProfileClient googleProfileClient;
    private UserService userService;
    private WaypointSessionService sessionService;
    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleProfileClient = mock(GoogleProfileClient.class);
        userService = mock(UserService.class);
        sessionService = mock(WaypointSessionService.class);
        googleAuthService = new GoogleAuthService(
                googleProfileClient,
                userService,
                sessionService,
                new GoogleProperties(
                        "expected-google-client",
                        "https://oauth2.googleapis.com/tokeninfo",
                        "https://www.googleapis.com/oauth2/v3/userinfo"
                )
        );
    }

    @Test
    void hidesProviderFailureDetailsFromClient() {
        when(googleProfileClient.fetchProfile("bad-token"))
                .thenThrow(new UnauthorizedException("provider-specific failure"));

        assertThatThrownBy(() -> googleAuthService.login("bad-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google authentication failed");
        verifyNoInteractions(userService, sessionService);
    }

    @Test
    void rejectsMissingProviderUserId() {
        assertRejected(new GoogleProfile(null, "user@example.com", true, "User", null,
                "expected-google-client", 300));
    }

    @Test
    void rejectsUnverifiedEmail() {
        assertRejected(new GoogleProfile("google-user", "user@example.com", false, "User", null,
                "expected-google-client", 300));
    }

    @Test
    void rejectsMismatchedAudience() {
        assertRejected(new GoogleProfile("google-user", "user@example.com", true, "User", null,
                "different-google-client", 300));
    }

    @Test
    void rejectsExpiredGoogleToken() {
        assertRejected(new GoogleProfile("google-user", "user@example.com", true, "User", null,
                "expected-google-client", 0));
    }

    private void assertRejected(GoogleProfile profile) {
        when(googleProfileClient.fetchProfile("google-token")).thenReturn(profile);
        assertThatThrownBy(() -> googleAuthService.login("google-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google authentication failed");
        verifyNoInteractions(userService, sessionService);
    }
}
