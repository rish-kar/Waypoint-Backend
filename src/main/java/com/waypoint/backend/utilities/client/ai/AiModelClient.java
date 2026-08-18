package com.waypoint.backend.utilities.client.ai;

import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;

public interface AiModelClient {
    AiIntentResponse route(AiIntentRequest request);

    AiChatResponse chat(AiChatRequest request);

    String modelId();

    boolean enabled();
}
