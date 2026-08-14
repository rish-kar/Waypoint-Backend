package com.waypoint.backend.utilities.client.lemonsqueezy;

import com.sun.net.httpserver.HttpServer;
import com.waypoint.backend.config.billing.LemonSqueezyProperties;
import com.waypoint.backend.model.subscription.CheckoutPlan;
import com.waypoint.backend.model.user.UserEntity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LemonSqueezyWebClientTests {
    private HttpServer server;
    private AtomicReference<String> requestBody;
    private AtomicReference<String> authorizationHeader;

    @BeforeEach
    void setUp() throws IOException {
        requestBody = new AtomicReference<>();
        authorizationHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/checkouts", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"data\":{\"attributes\":{\"url\":\"https://checkout.example/session\"}}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.api+json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createsCheckoutLockedToRequestedVariantAndPassesWaypointIdentity() throws Exception {
        LemonSqueezyProperties properties = new LemonSqueezyProperties(
                "secret-api-key",
                "123",
                "111",
                "222",
                "webhook-secret",
                "http://localhost:" + server.getAddress().getPort()
        );
        LemonSqueezyWebClient client = new LemonSqueezyWebClient(WebClient.builder(), properties);
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        String checkoutUrl = client.createCheckout(user, CheckoutPlan.MONTHLY, "111");

        assertThat(checkoutUrl).isEqualTo("https://checkout.example/session");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer secret-api-key");

        JsonNode payload = new ObjectMapper().readTree(requestBody.get());
        assertThat(payload.at("/data/relationships/store/data/id").asText()).isEqualTo("123");
        assertThat(payload.at("/data/relationships/variant/data/id").asText()).isEqualTo("111");
        assertThat(payload.at("/data/attributes/product_options/enabled_variants/0").asLong()).isEqualTo(111L);
        assertThat(payload.at("/data/attributes/checkout_data/email").asText()).isEqualTo("user@example.com");
        assertThat(payload.at("/data/attributes/checkout_data/custom/waypoint_user_id").asText())
                .isEqualTo(user.getId().toString());
        assertThat(payload.at("/data/attributes/checkout_data/custom/waypoint_plan").asText())
                .isEqualTo("MONTHLY");
    }
}
