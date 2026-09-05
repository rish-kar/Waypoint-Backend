package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.application.AppProperties;
import com.waypoint.backend.config.auth.GoogleOAuthProperties;
import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.model.auth.GoogleAuthStartResponse;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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
    void buildsBackendControlledGoogleAuthorizationUrl() {
        GoogleOAuthWebService service = service();

        URI authorizationUri = service.authorizationUri(
                "https://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.chromiumapp.org/google"
        );
        String authorizationUrl = URLDecoder.decode(authorizationUri.toString(), StandardCharsets.UTF_8);

        assertThat(authorizationUrl)
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=test-google-client")
                .contains("response_type=code")
                .contains("scope=openid email profile")
                .contains("state=")
                .contains("code_challenge=")
                .contains("code_challenge_method=S256")
                .contains("redirect_uri=http://localhost:8080/api/v1/auth/google/callback");
    }

    @Test
    void startsManualLoginWithoutClientCredentialsFromCaller() {
        GoogleOAuthWebService service = service();

        GoogleAuthStartResponse response = service.startManualLogin();
        String authorizationUrl = URLDecoder.decode(response.authorizationUrl(), StandardCharsets.UTF_8);

        assertThat(authorizationUrl)
                .contains("client_id=test-google-client")
                .contains("redirect_uri=http://localhost:8080/api/v1/auth/google/callback")
                .contains("scope=openid email profile")
                .contains("code_challenge_method=S256");
        assertThat(response.exchangeCode()).startsWith("g_");
        assertThat(response.expiresIn()).isEqualTo(300);
        assertThat(service.supportsManualExchangeCode(response.exchangeCode())).isTrue();
    }

    @Test
    void manualExchangeCannotCompleteBeforeGoogleCallback() {
        GoogleOAuthWebService service = service();
        GoogleAuthStartResponse response = service.startManualLogin();

        assertThatThrownBy(() -> service.exchangeManualLogin(response.exchangeCode()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("has not completed");
    }

    @Test
    void manualProviderFailureIsConsumedAsFailedExchange() {
        GoogleOAuthWebService service = service();
        GoogleAuthStartResponse response = service.startManualLogin();
        String state = UriComponentsBuilder.fromUriString(response.authorizationUrl())
                .build()
                .getQueryParams()
                .getFirst("state");

        GoogleOAuthWebService.CallbackResult result = service.callback(null, state, "access_denied");

        assertThat(result.manual()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(result.redirectUri()).isNull();
        assertThatThrownBy(() -> service.exchangeManualLogin(response.exchangeCode()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Google authentication failed");
    }

    @Test
    void createsFreshStateForEachLoginAttempt() {
        GoogleOAuthWebService service = service();
        String returnUrl = "https://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.chromiumapp.org/google";

        assertThat(service.authorizationUri(returnUrl)).isNotEqualTo(service.authorizationUri(returnUrl));
    }

    @Test
    void rejectsNonExtensionReturnUrls() {
        GoogleOAuthWebService service = service();

        assertThatThrownBy(() -> service.authorizationUri("https://example.com/google"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid extension return URL");
    }
}
