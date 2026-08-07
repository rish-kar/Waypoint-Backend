package com.waypoint.backend.utilities.client.google;

import com.waypoint.backend.config.auth.GoogleProperties;
import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleWebClientProfileClientTests {
    private HttpServer server;
    private String tokenInfoJson;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tokeninfo", exchange -> {
            byte[] bytes = tokenInfoJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/userinfo", exchange -> {
            byte[] bytes = """
                    {
                      "sub": "google-123",
                      "email": "user@example.com",
                      "email_verified": true,
                      "name": "User Name",
                      "picture": "https://example.com/picture.png"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void rejectsTokenInfoWithoutAudience() {
        tokenInfoJson = """
                {
                  "user_id": "google-123",
                  "email": "user@example.com",
                  "verified_email": true,
                  "expires_in": 300
                }
                """;

        assertThatThrownBy(() -> client().fetchProfile("google-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void rejectsTokenInfoWithMismatchedAudience() {
        tokenInfoJson = """
                {
                  "audience": "other-client",
                  "issued_to": "other-client",
                  "user_id": "google-123",
                  "email": "user@example.com",
                  "verified_email": true,
                  "expires_in": 300
                }
                """;

        assertThatThrownBy(() -> client().fetchProfile("google-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void rejectsExpiredTokenInfo() {
        tokenInfoJson = """
                {
                  "audience": "expected-client",
                  "issued_to": "expected-client",
                  "user_id": "google-123",
                  "email": "user@example.com",
                  "verified_email": true,
                  "expires_in": 0
                }
                """;

        assertThatThrownBy(() -> client().fetchProfile("google-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void acceptsGoogleOauth2AccessTokenInfoSchema() {
        tokenInfoJson = """
                {
                  "audience": "expected-client",
                  "issued_to": "expected-client",
                  "user_id": "google-123",
                  "email": "user@example.com",
                  "verified_email": true,
                  "expires_in": 300
                }
                """;

        GoogleProfile profile = client().fetchProfile("google-token");

        assertThat(profile.providerUserId()).isEqualTo("google-123");
        assertThat(profile.email()).isEqualTo("user@example.com");
        assertThat(profile.emailVerified()).isTrue();
        assertThat(profile.audience()).isEqualTo("expected-client");
        assertThat(profile.expiresInSeconds()).isEqualTo(300);
    }

    @Test
    void acceptsOidcCompatibleTokenInfoFieldNames() {
        tokenInfoJson = """
                {
                  "aud": "expected-client",
                  "sub": "google-123",
                  "email": "user@example.com",
                  "email_verified": true,
                  "expires_in": 300
                }
                """;

        GoogleProfile profile = client().fetchProfile("google-token");

        assertThat(profile.providerUserId()).isEqualTo("google-123");
        assertThat(profile.audience()).isEqualTo("expected-client");
    }

    private GoogleWebClientProfileClient client() {
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;
        return new GoogleWebClientProfileClient(
                WebClient.builder(),
                new GoogleProperties("expected-client", baseUrl + "/tokeninfo", baseUrl + "/userinfo")
        );
    }
}
