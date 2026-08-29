package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.application.AppProperties;
import com.waypoint.backend.config.auth.GoogleOAuthProperties;
import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.model.auth.GoogleOAuthStartResponse;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleOAuthWebServiceTests {
    private GoogleOAuthWebService service() {
        return new GoogleOAuthWebService(
                new GoogleProperties(
                        "test-google-client",
                        "https://oauth2.googleapis.com/tokeninfo",
                        "https://openidconnect.googleapis.com/v1/userinfo"
                ),
                new GoogleOAuthProperties(
                        "test-google-secret",
                        "https://accounts.google.com/o/oauth2/v2/auth",
                        "https://oauth2.googleapis.com/token"
                ),
                new AppProperties("http://localhost:8080"),
                null
        );
    }

    @Test
    void startsBackendControlledGoogleLogin() {
        GoogleOAuthWebService service = service();

        GoogleOAuthStartResponse response = service.start();

        assertThat(response.transactionId()).matches("[A-Za-z0-9_-]{32,128}");
        assertThat(response.authorizationUrl())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=test-google-client")
                .contains("response_type=code")
                .contains("scope=openid%20email%20profile")
                .contains("state=")
                .contains("code_challenge=")
                .contains("code_challenge_method=S256")
                .contains("redirect_uri=http://localhost:8080/api/v1/auth/google/callback");
        assertThat(response.expiresIn()).isPositive();
        assertThat(service.status(response.transactionId()).status()).isEqualTo("PENDING");
    }

    @Test
    void rejectsExchangeUntilCallbackCompletes() {
        GoogleOAuthWebService service = service();
        GoogleOAuthStartResponse response = service.start();

        assertThatThrownBy(() -> service.exchange(response.transactionId()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("still pending");
    }

    @Test
    void treatsUnknownTransactionsAsExpired() {
        GoogleOAuthWebService service = service();

        assertThat(service.status("not-a-valid-transaction").status()).isEqualTo("EXPIRED");
    }
}
