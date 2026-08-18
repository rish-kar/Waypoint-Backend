package com.waypoint.backend.utilities.client.ai;

import com.sun.net.httpserver.HttpServer;
import com.waypoint.backend.config.ai.AiProperties;
import com.waypoint.backend.model.ai.AiChatMessage;
import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.utilities.exception.ExternalServiceException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelfHostedAiClientTests {
    private HttpServer server;
    private AtomicReference<String> requestBody;
    private AtomicReference<String> authorizationHeader;
    private AtomicReference<String> responseContent;
    private AtomicInteger requestCount;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        requestBody = new AtomicReference<>();
        authorizationHeader = new AtomicReference<>();
        requestCount = new AtomicInteger();
        responseContent = new AtomicReference<>("{\"kind\":\"browser-action\",\"action\":\"group-tabs\",\"scope\":\"matching-tabs\",\"target\":\"project tabs\",\"matchTerms\":[\"project\",\"projects\"],\"sites\":[],\"explicitCurrent\":false,\"explicitAll\":false,\"groupTitle\":\"Project\",\"workspaceName\":\"\",\"wakeAt\":\"\",\"clarification\":\"\"}");
        objectMapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String response = "{\"choices\":[{\"message\":{\"content\":"
                    + objectMapper.writeValueAsString(responseContent.get())
                    + "}}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void routesIntentThroughOpenAiCompatibleStructuredOutput() throws Exception {
        SelfHostedAiClient client = client("secret-key");

        AiIntentResponse response = client.route(new AiIntentRequest(
                "Group my project tabs",
                false,
                "",
                "2026-08-18T18:00:00+05:30",
                "Asia/Kolkata",
                "en-IN",
                "self-hosted"
        ));

        assertThat(response.kind()).isEqualTo("browser-action");
        assertThat(response.action()).isEqualTo("group-tabs");
        assertThat(response.scope()).isEqualTo("matching-tabs");
        assertThat(response.matchTerms()).containsExactly("project", "projects");
        assertThat(response.modelId()).isEqualTo("self-hosted");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer secret-key");

        JsonNode outbound = objectMapper.readTree(requestBody.get());
        assertThat(outbound.path("model").asText()).isEqualTo("test-model");
        assertThat(outbound.path("temperature").asInt()).isZero();
        assertThat(outbound.at("/response_format/type").asText()).isEqualTo("json_schema");
        assertThat(outbound.at("/response_format/json_schema/strict").asBoolean()).isTrue();
        assertThat(outbound.at("/messages/0/content").asText()).contains("never choose or invent tab IDs");
        assertThat(outbound.at("/messages/1/content").asText()).contains("REQUEST: Group my project tabs");
    }

    @Test
    void answersFollowUpQuestionsWithRecentConversationHistory() throws Exception {
        responseContent.set("Her name is Jane.");
        SelfHostedAiClient client = client("");

        AiChatResponse response = client.chat(new AiChatRequest(
                "What is the name of his mother?",
                "John profile",
                "Profile page",
                "John is an engineer. His mother is Jane.",
                List.of(
                        new AiChatMessage("user", "Who is John?"),
                        new AiChatMessage("assistant", "John is the engineer described on this page.")
                ),
                false,
                "self-hosted"
        ));

        assertThat(response.answer()).isEqualTo("Her name is Jane.");
        assertThat(response.source()).isEqualTo("page");
        JsonNode outbound = objectMapper.readTree(requestBody.get());
        assertThat(outbound.at("/messages/1/content").asText()).isEqualTo("Who is John?");
        assertThat(outbound.at("/messages/2/content").asText()).contains("John is the engineer");
        assertThat(outbound.at("/messages/3/content").asText()).contains("QUESTION: What is the name of his mother?");
    }

    @Test
    void retriesMalformedIntentOnceBeforeFailingClosed() {
        responseContent.set("not-json");
        SelfHostedAiClient client = client("");

        assertThatThrownBy(() -> client.route(new AiIntentRequest(
                "Group tabs",
                false,
                "",
                "",
                "",
                "",
                "self-hosted"
        )))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("malformed structured output");
        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(authorizationHeader.get()).isNull();
    }

    private SelfHostedAiClient client(String apiKey) {
        AiProperties properties = new AiProperties(
                true,
                "http://localhost:" + server.getAddress().getPort() + "/v1",
                apiKey,
                "test-model",
                Duration.ofSeconds(2)
        );
        return new SelfHostedAiClient(WebClient.builder(), objectMapper, properties);
    }
}
