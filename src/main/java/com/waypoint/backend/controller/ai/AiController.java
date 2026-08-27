package com.waypoint.backend.controller.ai;

import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.model.ai.AiModelCatalogResponse;
import com.waypoint.backend.model.ai.AiUsageResponse;
import com.waypoint.backend.service.ai.AiIntentService;
import com.waypoint.backend.service.ai.AiUsageService;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private final AiIntentService aiIntentService;
    private final AiUsageService aiUsageService;

    public AiController(AiIntentService aiIntentService, AiUsageService aiUsageService) {
        this.aiIntentService = aiIntentService;
        this.aiUsageService = aiUsageService;
    }

    @GetMapping("/models")
    public AiModelCatalogResponse models() {
        return aiIntentService.models();
    }

    @GetMapping("/usage")
    public AiUsageResponse usage(@AuthenticationPrincipal UUID userId) {
        return aiUsageService.current(userId);
    }

    @PostMapping("/intent")
    public AiIntentResponse routeIntent(@Valid @RequestBody AiIntentRequest request) {
        return aiIntentService.route(request);
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiIntentService.chat(request);
    }
}
