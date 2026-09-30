package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.sun.net.httpserver.HttpServer;
import com.waypoint.backend.config.billing.LemonSqueezyProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LemonSqueezySubscriptionCancellationTests {
    private HttpServer server;
    private AtomicReference<String> method;
    private AtomicReference<String> authorization;

    @BeforeEach
    void setUp() throws IOException {
        method = new AtomicReference<>();
        authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/subscriptions/2514315", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = """
                    {"data":{"type":"subscriptions","id":"2514315","attributes":{"status":"cancelled"}}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.api+json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/subscriptions/404", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void cancelsProviderSubscriptionBeforeLocalAccountDeletion() {
        LemonSqueezySubscriptionWebClient client =
                new LemonSqueezySubscriptionWebClient(WebClient.builder(), properties());

        client.cancelSubscription("2514315");

        assertThat(method.get()).isEqualTo("DELETE");
        assertThat(authorization.get()).isEqualTo("Bearer secret-api-key");
    }

    @Test
    void treatsAlreadyMissingProviderSubscriptionAsSafeToDeleteLocally() {
        LemonSqueezySubscriptionWebClient client =
                new LemonSqueezySubscriptionWebClient(WebClient.builder(), properties());

        client.cancelSubscription("404");
    }

    private LemonSqueezyProperties properties() {
        return new LemonSqueezyProperties(
                "secret-api-key",
                "123",
                "111",
                "222",
                "webhook-secret",
                "http://localhost:" + server.getAddress().getPort()
        );
    }
}
