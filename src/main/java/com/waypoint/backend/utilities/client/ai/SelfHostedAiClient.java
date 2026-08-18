package com.waypoint.backend.utilities.client.ai;

import com.waypoint.backend.config.ai.AiProperties;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.utilities.exception.AiUnavailableException;
import com.waypoint.backend.utilities.exception.ExternalServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
public class SelfHostedAiClient implements AiModelClient {
    public static final String MODEL_ID = "self-hosted";

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfHostedAiClient.class);
    private static final int MAX_TERMS = 16;
    private static final int MAX_TERM_LENGTH = 80;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;
    private final URI chatCompletionsUri;

    public SelfHostedAiClient(WebClient.Builder builder, ObjectMapper objectMapper, AiProperties properties) {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.chatCompletionsUri = URI.create(stripTrailingSlash(properties.baseUrl()) + "/chat/completions");
    }

    @Override
    public AiIntentResponse route(AiIntentRequest request) {
        if (!enabled()) {
            throw new AiUnavailableException("Self-hosted AI is not enabled");
        }

        WebClient.RequestBodySpec outbound = webClient.post()
                .uri(chatCompletionsUri)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.apiKey())) {
            outbound.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey().trim());
        }

        try {
            JsonNode response = outbound
                    .bodyValue(requestBody(request))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(properties.requestTimeout())
                    .block();
            return parseResponse(response);
        } catch (WebClientResponseException exception) {
            LOGGER.warn("Self-hosted AI returned HTTP status {}", exception.getStatusCode().value());
            throw new ExternalServiceException("Self-hosted AI rejected the request");
        } catch (WebClientRequestException exception) {
            LOGGER.warn("Unable to reach self-hosted AI", exception);
            throw new AiUnavailableException("Self-hosted AI is unavailable");
        } catch (ExternalServiceException | AiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (containsTimeout(exception)) {
                throw new ExternalServiceException("Self-hosted AI took too long to respond");
            }
            throw exception;
        }
    }

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    private Map<String, Object> requestBody(AiIntentRequest request) {
        return Map.of(
                "model", properties.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", userPrompt(request))
                ),
                "temperature", 0,
                "stream", false,
                "max_tokens", 500,
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "waypoint_intent",
                                "strict", true,
                                "schema", intentSchema()
                        )
                )
        );
    }

    private AiIntentResponse parseResponse(JsonNode response) {
        String content = response == null ? null : response.at("/choices/0/message/content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new ExternalServiceException("Self-hosted AI returned an invalid response");
        }

        try {
            JsonNode intent = objectMapper.readTree(content);
            return new AiIntentResponse(
                    text(intent, "kind", 40),
                    text(intent, "action", 40),
                    text(intent, "scope", 40),
                    text(intent, "target", 160),
                    stringList(intent, "matchTerms"),
                    stringList(intent, "sites"),
                    bool(intent, "explicitCurrent"),
                    bool(intent, "explicitAll"),
                    text(intent, "groupTitle", 80),
                    text(intent, "workspaceName", 64),
                    text(intent, "wakeAt", 80),
                    text(intent, "clarification", 220),
                    MODEL_ID
            );
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExternalServiceException("Self-hosted AI returned malformed structured output");
        }
    }

    private String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) {
            throw new ExternalServiceException("Self-hosted AI returned malformed structured output");
        }
        String text = value.asText().trim();
        if (text.length() > maxLength) {
            throw new ExternalServiceException("Self-hosted AI returned malformed structured output");
        }
        return text;
    }

    private boolean bool(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isBoolean()) {
            throw new ExternalServiceException("Self-hosted AI returned malformed structured output");
        }
        return value.asBoolean();
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray() || value.size() > MAX_TERMS) {
            throw new ExternalServiceException("Self-hosted AI returned malformed structured output");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw new ExternalServiceException("Self-hosted AI returned malformed structured output");
            }
            String text = item.asText().trim();
            if (text.isBlank() || text.length() > MAX_TERM_LENGTH) {
                throw new ExternalServiceException("Self-hosted AI returned malformed structured output");
            }
            if (!values.contains(text)) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private String userPrompt(AiIntentRequest request) {
        String currentTime = StringUtils.hasText(request.currentTime())
                ? request.currentTime().trim()
                : OffsetDateTime.now(ZoneOffset.UTC).toString();
        return String.join("\n",
                "TASK: INTENT",
                "CURRENT_TIME: " + currentTime,
                "TIME_ZONE: " + cleanOptional(request.timeZone(), "unknown"),
                "LOCALE: " + cleanOptional(request.locale(), "unknown"),
                "LAST_SELECTION_AVAILABLE: " + request.lastSelectionAvailable(),
                "LAST_SELECTION_TARGET: " + cleanOptional(request.lastSelectionTarget(), "none"),
                "REQUEST: " + request.request().trim(),
                "Return only the structured intent. Never return tab IDs."
        );
    }

    private String systemPrompt() {
        return String.join("\n",
                "You are the natural-language intent router for Waypoint browser actions.",
                "You never receive browser tab candidates and you never choose or invent tab IDs.",
                "JavaScript deterministically matches your structured target against the user's real open tabs after you respond.",
                "Classify normal page questions as not-browser-action so Page AI can answer them normally.",
                "Allowed actions are group-tabs, ungroup-tabs, close-duplicates, close-tabs, snooze-tabs and save-workspace.",
                "If the user asks for another browser capability, return unsupported-action with a short explanation.",
                "Set explicitCurrent=true only when the user explicitly refers to this tab, the current tab or the active tab; otherwise false.",
                "Use scope=current-tab only when explicitCurrent=true. Named websites, topics, projects and categories use matching-tabs.",
                "Use previous-selection only for references such as them, those tabs or the same tabs when LAST_SELECTION_AVAILABLE is true.",
                "Use all-tabs only when the user explicitly means the entire current window, and then set explicitAll=true.",
                "Use duplicates for close-duplicates. Otherwise use matching-tabs for named websites, topics, projects or categories.",
                "Preserve the user's semantic target in target instead of replacing it with an action-specific label.",
                "For matchTerms, include concise semantic terms that help deterministic matching, including singular/plural base forms and close synonyms when justified by the request.",
                "Do not invent unrelated brands, websites, projects or topics.",
                "sites contains canonical hostnames only when a website or domain is clearly identified.",
                "For group-tabs, derive a concise groupTitle from the requested target.",
                "For save-workspace, return the requested workspaceName or clarify when it is missing.",
                "For snooze-tabs, derive wakeAt as an absolute ISO-8601 date-time using CURRENT_TIME and TIME_ZONE.",
                "When the target is vague or subjective, return clarification instead of guessing.",
                "Never return, infer or fabricate a Chrome tab ID."
        );
    }

    private Map<String, Object> intentSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("kind", Map.of(
                "type", "string",
                "enum", List.of("browser-action", "not-browser-action", "clarification", "unsupported-action")
        ));
        properties.put("action", Map.of(
                "type", "string",
                "enum", List.of("group-tabs", "ungroup-tabs", "close-duplicates", "close-tabs", "snooze-tabs", "save-workspace", "none")
        ));
        properties.put("scope", Map.of(
                "type", "string",
                "enum", List.of("matching-tabs", "current-tab", "previous-selection", "all-tabs", "duplicates", "none")
        ));
        properties.put("target", stringSchema(160));
        properties.put("matchTerms", arraySchema());
        properties.put("sites", arraySchema());
        properties.put("explicitCurrent", Map.of("type", "boolean"));
        properties.put("explicitAll", Map.of("type", "boolean"));
        properties.put("groupTitle", stringSchema(80));
        properties.put("workspaceName", stringSchema(64));
        properties.put("wakeAt", stringSchema(80));
        properties.put("clarification", stringSchema(220));

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of(
                        "kind",
                        "action",
                        "scope",
                        "target",
                        "matchTerms",
                        "sites",
                        "explicitCurrent",
                        "explicitAll",
                        "groupTitle",
                        "workspaceName",
                        "wakeAt",
                        "clarification"
                ),
                "additionalProperties", false
        );
    }

    private Map<String, Object> stringSchema(int maxLength) {
        return Map.of("type", "string", "maxLength", maxLength);
    }

    private Map<String, Object> arraySchema() {
        return Map.of(
                "type", "array",
                "maxItems", MAX_TERMS,
                "items", Map.of("type", "string", "minLength", 1, "maxLength", MAX_TERM_LENGTH)
        );
    }

    private String cleanOptional(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String stripTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean containsTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
