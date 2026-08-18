package com.waypoint.backend.utilities.client.ai;

import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;

public interface AiModelClient {
    AiIntentResponse route(AiIntentRequest request);

    String modelId();

    boolean enabled();
}
