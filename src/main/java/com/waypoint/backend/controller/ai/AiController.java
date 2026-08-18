package com.waypoint.backend.controller.ai;

import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.model.ai.AiModelCatalogResponse;
import com.waypoint.backend.service.ai.AiIntentService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private final AiIntentService aiIntentService;

    public AiController(AiIntentService aiIntentService) {
        this.aiIntentService = aiIntentService;
    }

    @GetMapping("/models")
    public AiModelCatalogResponse models() {
        return aiIntentService.models();
    }

    @PostMapping("/intent")
    public AiIntentResponse routeIntent(@Valid @RequestBody AiIntentRequest request) {
        return aiIntentService.route(request);
    }
}
