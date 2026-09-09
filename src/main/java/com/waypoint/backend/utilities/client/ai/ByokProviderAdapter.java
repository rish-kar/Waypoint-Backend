package com.waypoint.backend.utilities.client.ai;

import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.model.ai.ByokProvider;

import java.util.List;

public interface ByokProviderAdapter {
    boolean supports(ByokProvider provider);

    List<String> availableModels(ByokProvider provider, String apiKey);

    AiIntentResponse route(ByokProvider provider, AiIntentRequest request, String apiKey, String model);

    AiChatResponse chat(ByokProvider provider, AiChatRequest request, String apiKey, String model);
}
