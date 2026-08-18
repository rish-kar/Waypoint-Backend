from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def write(path, content):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


write("src/main/java/com/waypoint/backend/model/ai/AiChatMessage.java", '''package com.waypoint.backend.model.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiChatMessage(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = 1200) String text
) {
}
''')

write("src/main/java/com/waypoint/backend/model/ai/AiChatRequest.java", '''package com.waypoint.backend.model.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiChatRequest(
        @NotBlank @Size(max = 500) String question,
        @Size(max = 220) String pageTitle,
        @Size(max = 1000) String pageDescription,
        @NotBlank @Size(max = 14000) String pageText,
        @Valid @Size(max = 12) List<AiChatMessage> history,
        boolean allowGeneral,
        @Size(max = 40) String model
) {
}
''')

write("src/main/java/com/waypoint/backend/model/ai/AiChatResponse.java", '''package com.waypoint.backend.model.ai;

public record AiChatResponse(
        String answer,
        String source,
        String modelId
) {
}
''')

# AiModelClient
p = ROOT / "src/main/java/com/waypoint/backend/utilities/client/ai/AiModelClient.java"
s = p.read_text(encoding="utf-8")
s = s.replace("import com.waypoint.backend.model.ai.AiIntentRequest;", "import com.waypoint.backend.model.ai.AiChatRequest;\nimport com.waypoint.backend.model.ai.AiChatResponse;\nimport com.waypoint.backend.model.ai.AiIntentRequest;")
s = s.replace("    AiIntentResponse route(AiIntentRequest request);\n", "    AiIntentResponse route(AiIntentRequest request);\n\n    AiChatResponse chat(AiChatRequest request);\n")
p.write_text(s, encoding="utf-8")

# Controller
p = ROOT / "src/main/java/com/waypoint/backend/controller/ai/AiController.java"
s = p.read_text(encoding="utf-8")
s = s.replace("import com.waypoint.backend.model.ai.AiIntentRequest;", "import com.waypoint.backend.model.ai.AiChatRequest;\nimport com.waypoint.backend.model.ai.AiChatResponse;\nimport com.waypoint.backend.model.ai.AiIntentRequest;")
s = s.replace("    @PostMapping(\"/intent\")\n    public AiIntentResponse routeIntent(@Valid @RequestBody AiIntentRequest request) {\n        return aiIntentService.route(request);\n    }\n", "    @PostMapping(\"/intent\")\n    public AiIntentResponse routeIntent(@Valid @RequestBody AiIntentRequest request) {\n        return aiIntentService.route(request);\n    }\n\n    @PostMapping(\"/chat\")\n    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {\n        return aiIntentService.chat(request);\n    }\n")
p.write_text(s, encoding="utf-8")

# Service
p = ROOT / "src/main/java/com/waypoint/backend/service/ai/AiIntentService.java"
s = p.read_text(encoding="utf-8")
s = s.replace("import com.waypoint.backend.model.ai.AiIntentRequest;", "import com.waypoint.backend.model.ai.AiChatRequest;\nimport com.waypoint.backend.model.ai.AiChatResponse;\nimport com.waypoint.backend.model.ai.AiIntentRequest;")
needle = '''    public AiIntentResponse route(AiIntentRequest request) {
        String requestedModel = normalizeModel(request.model());
        if (!selfHostedClient.modelId().equals(requestedModel)) {
            throw new InvalidRequestException("Unsupported AI model: " + requestedModel);
        }
        if (!selfHostedClient.enabled()) {
            throw new AiUnavailableException("The selected AI model is not enabled");
        }
        return normalize(selfHostedClient.route(request));
    }
'''
replacement = needle + '''
    public AiChatResponse chat(AiChatRequest request) {
        String requestedModel = normalizeModel(request.model());
        if (!selfHostedClient.modelId().equals(requestedModel)) {
            throw new InvalidRequestException("Unsupported AI model: " + requestedModel);
        }
        if (!selfHostedClient.enabled()) {
            throw new AiUnavailableException("The selected AI model is not enabled");
        }
        AiChatResponse response = selfHostedClient.chat(request);
        if (response == null || !StringUtils.hasText(response.answer())) {
            throw new ExternalServiceException("Cloud AI returned an empty answer");
        }
        return response;
    }
'''
if needle not in s:
    raise SystemExit("AiIntentService route block not found")
s = s.replace(needle, replacement)
s = s.replace('                "Self-hosted AI",', '                "Cloud AI",')
s = s.replace('return new ExternalServiceException("Self-hosted AI returned an invalid intent");', 'return new ExternalServiceException("Cloud AI returned an invalid intent");')
p.write_text(s, encoding="utf-8")

# SelfHostedAiClient imports and implementation
p = ROOT / "src/main/java/com/waypoint/backend/utilities/client/ai/SelfHostedAiClient.java"
s = p.read_text(encoding="utf-8")
s = s.replace("import com.waypoint.backend.model.ai.AiIntentRequest;", "import com.waypoint.backend.model.ai.AiChatMessage;\nimport com.waypoint.backend.model.ai.AiChatRequest;\nimport com.waypoint.backend.model.ai.AiChatResponse;\nimport com.waypoint.backend.model.ai.AiIntentRequest;")
start = s.index("    @Override\n    public AiIntentResponse route(AiIntentRequest request) {")
end = s.index("\n    @Override\n    public String modelId()", start)
new_methods = '''    @Override
    public AiIntentResponse route(AiIntentRequest request) {
        requireEnabled();
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return parseResponse(send(requestBody(request)));
            } catch (ExternalServiceException | AiUnavailableException exception) {
                last = exception;
                if (attempt == 0) {
                    LOGGER.warn("Cloud AI intent attempt failed; retrying once: {}", exception.getMessage());
                    continue;
                }
                throw exception;
            }
        }
        throw last == null ? new ExternalServiceException("Cloud AI failed") : last;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        requireEnabled();
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String answer = completion(pageChatBody(request));
                if (!answer.contains("[[WAYPOINT_NOT_FOUND]]")) {
                    return new AiChatResponse(answer, "page", MODEL_ID);
                }
                if (!request.allowGeneral()) {
                    return new AiChatResponse("I couldn't find that information on this page.", "page", MODEL_ID);
                }
                String general = completion(generalChatBody(request));
                return new AiChatResponse(general, "general", MODEL_ID);
            } catch (ExternalServiceException | AiUnavailableException exception) {
                last = exception;
                if (attempt == 0) {
                    LOGGER.warn("Cloud AI chat attempt failed; retrying once: {}", exception.getMessage());
                    continue;
                }
                throw exception;
            }
        }
        throw last == null ? new ExternalServiceException("Cloud AI failed") : last;
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new AiUnavailableException("Cloud AI is not enabled");
        }
    }

    private JsonNode send(Map<String, Object> body) {
        WebClient.RequestBodySpec outbound = webClient.post()
                .uri(chatCompletionsUri)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.apiKey())) {
            outbound.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey().trim());
        }
        try {
            return outbound
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(properties.requestTimeout())
                    .block();
        } catch (WebClientResponseException exception) {
            LOGGER.warn("Cloud AI returned HTTP status {}", exception.getStatusCode().value());
            throw new ExternalServiceException("Cloud AI rejected the request");
        } catch (WebClientRequestException exception) {
            LOGGER.warn("Unable to reach Cloud AI", exception);
            throw new AiUnavailableException("Cloud AI is unavailable");
        } catch (ExternalServiceException | AiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (containsTimeout(exception)) {
                throw new ExternalServiceException("Cloud AI took too long to respond");
            }
            throw exception;
        }
    }

    private String completion(Map<String, Object> body) {
        JsonNode response = send(body);
        String content = response == null ? null : response.at("/choices/0/message/content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new ExternalServiceException("Cloud AI returned an empty response");
        }
        return content.trim();
    }

    private Map<String, Object> pageChatBody(AiChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", String.join("\\n",
                "You are Waypoint's Cloud page assistant.",
                "Use PAGE CONTEXT as the only evidence for factual claims on the first attempt.",
                "Use conversation history to resolve follow-up references such as he, she, it, they, that person or that topic.",
                "Conversation history is context, not independent webpage evidence.",
                "If PAGE CONTEXT does not support the answer, reply exactly [[WAYPOINT_NOT_FOUND]].",
                "Otherwise answer directly and concisely."
        )));
        appendHistory(messages, request.history());
        String prompt = String.join("\\n\\n",
                "PAGE TITLE: " + cleanOptional(request.pageTitle(), "Untitled page"),
                StringUtils.hasText(request.pageDescription()) ? "PAGE DESCRIPTION: " + request.pageDescription().trim() : "",
                "PAGE CONTEXT:\\n" + request.pageText().trim(),
                "QUESTION: " + request.question().trim()
        );
        messages.add(Map.of("role", "user", "content", prompt));
        return plainChatBody(messages, 700);
    }

    private Map<String, Object> generalChatBody(AiChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", String.join("\\n",
                "You are Waypoint's Cloud assistant.",
                "The webpage did not contain the answer, so answer from general knowledge.",
                "Use conversation history to resolve follow-up references.",
                "Be direct and concise. Do not claim to browse the internet."
        )));
        appendHistory(messages, request.history());
        messages.add(Map.of("role", "user", "content", request.question().trim()));
        return plainChatBody(messages, 700);
    }

    private void appendHistory(List<Map<String, String>> messages, List<AiChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return;
        }
        int start = Math.max(0, history.size() - 10);
        for (AiChatMessage item : history.subList(start, history.size())) {
            if (item == null || !StringUtils.hasText(item.text())) {
                continue;
            }
            String role = "assistant".equals(item.role()) ? "assistant" : "user";
            messages.add(Map.of("role", role, "content", item.text().trim()));
        }
    }

    private Map<String, Object> plainChatBody(List<Map<String, String>> messages, int maxTokens) {
        return Map.of(
                "model", properties.model(),
                "messages", messages,
                "temperature", 0.1,
                "stream", false,
                "max_tokens", maxTokens
        );
    }
'''
s = s[:start] + new_methods + s[end:]
# User-facing/internal error naming cleanup in this class only.
s = s.replace("Self-hosted AI returned an invalid response", "Cloud AI returned an invalid response")
s = s.replace("Self-hosted AI returned malformed structured output", "Cloud AI returned malformed structured output")
p.write_text(s, encoding="utf-8")

print("Cloud AI chat integration applied")
