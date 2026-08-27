package com.waypoint.backend.utilities.client.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import com.waypoint.backend.config.ai.OpenAiProperties;
import com.waypoint.backend.model.ai.AiChatMessage;
import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.utilities.exception.AiUnavailableException;
import com.waypoint.backend.utilities.exception.ExternalServiceException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiClientTests {
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
                    + "}}],\"usage\":{\"prompt_tokens\":123,\"completion_tokens\":17,\"total_tokens\":140}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("x-request-id", "req_test_123");
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
    void routesIntentThroughGpt5NanoStructuredOutput() throws Exception {
        OpenAiClient client = client("secret-key");

        AiIntentResponse response = client.route(new AiIntentRequest(
                "Group my project tabs",
                false,
                "",
                "2026-08-24T12:00:00+05:30",
                "Asia/Kolkata",
                "en-IN",
                OpenAiClient.MODEL_ID
        ));

        assertThat(response.kind()).isEqualTo("browser-action");
        assertThat(response.action()).isEqualTo("group-tabs");
        assertThat(response.modelId()).isEqualTo(OpenAiClient.MODEL_ID);
        assertThat(authorizationHeader.get()).isEqualTo("Bearer secret-key");

        JsonNode outbound = objectMapper.readTree(requestBody.get());
        assertThat(outbound.path("model").asText()).isEqualTo("gpt-5-nano");
        assertThat(outbound.path("reasoning_effort").asText()).isEqualTo("minimal");
        assertThat(outbound.path("temperature").isMissingNode()).isTrue();
        assertThat(outbound.path("max_completion_tokens").asInt()).isEqualTo(800);
        assertThat(outbound.at("/response_format/type").asText()).isEqualTo("json_schema");
        assertThat(outbound.at("/response_format/json_schema/strict").asBoolean()).isTrue();
        assertThat(outbound.at("/messages/0/role").asText()).isEqualTo("developer");
        assertThat(outbound.at("/messages/0/content").asText()).contains("never choose or invent tab IDs");
    }

    @Test
    void answersOnlyWhenPageEvidenceCanBeVerified() throws Exception {
        responseContent.set("Her name is Jane.\n[[WAYPOINT_EVIDENCE]]His mother is Jane.[[/WAYPOINT_EVIDENCE]]");
        OpenAiClient client = client("secret-key");

        AiChatResponse response = client.chat(new AiChatRequest(
                "What is the name of his mother?",
                "John profile",
                "Profile page",
                "John is an engineer. His mother is Jane.",
                List.of(
                        new AiChatMessage("user", "Who is John?"),
                        new AiChatMessage("assistant", "John is the engineer described on this page.")
                ),
                OpenAiClient.MODEL_ID
        ));

        assertThat(response.answer()).isEqualTo("Her name is Jane.");
        assertThat(response.source()).isEqualTo("page");
        JsonNode outbound = objectMapper.readTree(requestBody.get());
        assertThat(outbound.at("/messages/0/role").asText()).isEqualTo("developer");
        assertThat(outbound.at("/messages/3/content").asText()).contains("<WAYPOINT_UNTRUSTED_PAGE>");
    }

    @Test
    void telemetryContainsOnlyOperationalMetadata() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OpenAiClient.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            OpenAiClient client = client("private-secret-key");
            client.route(new AiIntentRequest(
                    "Group private.person@example.com tabs containing Highly private page text",
                    false,
                    "",
                    "",
                    "",
                    "",
                    OpenAiClient.MODEL_ID
            ));

            String logs = appender.list.stream()
                    .map(event -> event.getFormattedMessage() + " " + event.getKeyValuePairs())
                    .collect(Collectors.joining("\n"));

            assertThat(logs)
                    .contains("openai_request_completed")
                    .contains("req_test_123")
                    .contains("input_tokens")
                    .contains("123")
                    .contains("output_tokens")
                    .contains("17")
                    .contains("total_tokens")
                    .contains("140")
                    .contains("latency_ms")
                    .doesNotContain("private.person@example.com")
                    .doesNotContain("Highly private page text")
                    .doesNotContain("private-secret-key")
                    .doesNotContain(responseContent.get());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void retriesMalformedIntentOnceBeforeFailingClosed() {
        responseContent.set("not-json");
        OpenAiClient client = client("secret-key");

        assertThatThrownBy(() -> client.route(new AiIntentRequest(
                "Group tabs",
                false,
                "",
                "",
                "",
                "",
                OpenAiClient.MODEL_ID
        )))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("malformed structured output");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void refusesToRunWithoutApiKey() {
        OpenAiClient client = client("");

        assertThatThrownBy(() -> client.route(new AiIntentRequest(
                "Group tabs", false, "", "", "", "", OpenAiClient.MODEL_ID
        )))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("API key");
        assertThat(requestCount.get()).isZero();
    }

    private OpenAiClient client(String apiKey) {
        OpenAiProperties properties = new OpenAiProperties(
                true,
                "http://localhost:" + server.getAddress().getPort() + "/v1",
                apiKey,
                "gpt-5-nano",
                Duration.ofSeconds(2)
        );
        return new OpenAiClient(WebClient.builder(), objectMapper, properties);
    }
}
