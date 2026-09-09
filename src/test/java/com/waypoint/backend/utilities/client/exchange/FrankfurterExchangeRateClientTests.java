package com.waypoint.backend.utilities.client.exchange;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FrankfurterExchangeRateClientTests {
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/rate/INR/USD", exchange -> {
            byte[] bytes = """
                    {
                      "date": "2026-09-09",
                      "base": "INR",
                      "quote": "USD",
                      "rate": 0.01038
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
    void readsSinglePairRateFromFrankfurterV2Contract() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        FrankfurterExchangeRateClient client = new FrankfurterExchangeRateClient(WebClient.builder(), baseUrl);

        assertThat(client.rate("inr", "usd")).isEqualByComparingTo("0.01038");
        assertThat(client.rate("INR", "INR")).isEqualByComparingTo("1");
    }
}
