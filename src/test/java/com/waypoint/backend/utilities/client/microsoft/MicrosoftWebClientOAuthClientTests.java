package com.waypoint.backend.utilities.client.microsoft;

import com.waypoint.backend.config.auth.MicrosoftOAuthProperties;
import com.waypoint.backend.model.auth.MicrosoftProfile;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MicrosoftWebClientOAuthClientTests {
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/me", exchange -> {
            byte[] bytes = """
                    {
                      "id": "microsoft-123",
                      "mail": "user@example.com",
                      "displayName": "User Name",
                      "preferredLanguage": "en-GB"
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
    void capturesPreferredLanguageFromMicrosoftProfile() {
        MicrosoftProfile profile = client().fetchProfile("microsoft-token");

        assertThat(profile.providerUserId()).isEqualTo("microsoft-123");
        assertThat(profile.email()).isEqualTo("user@example.com");
        assertThat(profile.displayName()).isEqualTo("User Name");
        assertThat(profile.locale()).isEqualTo("en-GB");
    }

    private MicrosoftWebClientOAuthClient client() {
        int port = server.getAddress().getPort();
        String graphUrl = "http://127.0.0.1:" + port + "/me";
        MicrosoftOAuthProperties properties = new MicrosoftOAuthProperties(
                "client-id",
                "client-secret",
                "common",
                "http://localhost:8080/callback",
                graphUrl,
                "encryption-key",
                null,
                List.of("https://test-extension.chromiumapp.org/microsoft"),
                600,
                180,
                3600000
        );
        return new MicrosoftWebClientOAuthClient(WebClient.builder(), properties);
    }
}
