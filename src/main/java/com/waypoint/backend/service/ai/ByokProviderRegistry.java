package com.waypoint.backend.service.ai;

import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.model.ai.ByokProvider;
import com.waypoint.backend.model.ai.ByokProviderResponse;
import com.waypoint.backend.utilities.client.ai.ByokProviderAdapter;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ByokProviderRegistry {
    private final List<ByokProviderAdapter> adapters;

    public ByokProviderRegistry(List<ByokProviderAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public List<ByokProviderResponse> providers() {
        return Arrays.stream(ByokProvider.values())
                .filter(this::supported)
                .map(provider -> new ByokProviderResponse(provider.id(), provider.displayName()))
                .toList();
    }

    public List<String> availableModels(ByokProvider provider, String apiKey) {
        return adapter(provider).availableModels(provider, apiKey);
    }

    public AiIntentResponse route(ByokProvider provider, AiIntentRequest request, String apiKey, String model) {
        return adapter(provider).route(provider, request, apiKey, model);
    }

    public AiChatResponse chat(ByokProvider provider, AiChatRequest request, String apiKey, String model) {
        return adapter(provider).chat(provider, request, apiKey, model);
    }

    private boolean supported(ByokProvider provider) {
        return adapters.stream().anyMatch(adapter -> adapter.supports(provider));
    }

    private ByokProviderAdapter adapter(ByokProvider provider) {
        if (provider == null) {
            throw new InvalidRequestException("AI provider is required");
        }
        return adapters.stream()
                .filter(candidate -> candidate.supports(provider))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException(provider.displayName() + " is not supported"));
    }
}
