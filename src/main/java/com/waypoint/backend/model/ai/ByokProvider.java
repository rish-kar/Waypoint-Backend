package com.waypoint.backend.model.ai;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ByokProvider {
    OPENAI(
            "openai",
            "OpenAI",
            URI.create("https://api.openai.com/v1/chat/completions"),
            URI.create("https://api.openai.com/v1/models"),
            "data",
            ModelAuth.BEARER,
            "max_completion_tokens"
    ),
    ANTHROPIC(
            "anthropic",
            "Anthropic Claude",
            URI.create("https://api.anthropic.com/v1/chat/completions"),
            URI.create("https://api.anthropic.com/v1/models?limit=1000"),
            "data",
            ModelAuth.ANTHROPIC,
            "max_completion_tokens"
    ),
    GOOGLE(
            "google",
            "Google Gemini",
            URI.create("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"),
            URI.create("https://generativelanguage.googleapis.com/v1beta/openai/models"),
            "data",
            ModelAuth.BEARER,
            "max_completion_tokens"
    ),
    XAI(
            "xai",
            "xAI Grok",
            URI.create("https://api.x.ai/v1/chat/completions"),
            URI.create("https://api.x.ai/v1/language-models"),
            "models",
            ModelAuth.BEARER,
            "max_tokens"
    ),
    OPENROUTER(
            "openrouter",
            "OpenRouter",
            URI.create("https://openrouter.ai/api/v1/chat/completions"),
            URI.create("https://openrouter.ai/api/v1/models?output_modalities=text"),
            "data",
            ModelAuth.BEARER,
            "max_tokens"
    );

    private static final List<String> OPENAI_TEXT_PREFIXES = List.of(
            "gpt-5", "gpt-4.1", "gpt-4o", "o1", "o3", "o4"
    );
    private static final List<String> OPENAI_UNSUPPORTED_MARKERS = List.of(
            "audio", "realtime", "transcribe", "tts", "image", "search", "-pro", "codex", "cyber"
    );
    private static final List<String> GEMINI_UNSUPPORTED_MARKERS = List.of(
            "image", "embedding", "tts", "live", "veo", "imagen"
    );

    private final String id;
    private final String displayName;
    private final URI chatCompletionsUri;
    private final URI modelsUri;
    private final String modelArrayField;
    private final ModelAuth modelAuth;
    private final String maxTokensField;

    ByokProvider(
            String id,
            String displayName,
            URI chatCompletionsUri,
            URI modelsUri,
            String modelArrayField,
            ModelAuth modelAuth,
            String maxTokensField
    ) {
        this.id = id;
        this.displayName = displayName;
        this.chatCompletionsUri = chatCompletionsUri;
        this.modelsUri = modelsUri;
        this.modelArrayField = modelArrayField;
        this.modelAuth = modelAuth;
        this.maxTokensField = maxTokensField;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public URI chatCompletionsUri() {
        return chatCompletionsUri;
    }

    public URI modelsUri() {
        return modelsUri;
    }

    public String modelArrayField() {
        return modelArrayField;
    }

    public ModelAuth modelAuth() {
        return modelAuth;
    }

    public String maxTokensField() {
        return maxTokensField;
    }

    public boolean supportsModel(String model) {
        String value = model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return false;
        }
        return switch (this) {
            case OPENAI -> OPENAI_TEXT_PREFIXES.stream().anyMatch(value::startsWith)
                    && OPENAI_UNSUPPORTED_MARKERS.stream().noneMatch(value::contains);
            case ANTHROPIC -> value.startsWith("claude-");
            case GOOGLE -> value.startsWith("gemini-")
                    && GEMINI_UNSUPPORTED_MARKERS.stream().noneMatch(value::contains);
            case XAI -> !value.contains("embedding") && !value.contains("image") && !value.contains("video") && !value.contains("voice");
            case OPENROUTER -> value.contains("/");
        };
    }

    public static Optional<ByokProvider> find(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ByokProvider provider : values()) {
            if (provider.id.equals(normalized)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    public enum ModelAuth {
        BEARER,
        ANTHROPIC
    }
}
