package com.waypoint.backend.controller.ai;

import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.model.ai.AiModelCatalogResponse;
import com.waypoint.backend.model.ai.AiUsageResponse;
import com.waypoint.backend.model.ai.FamilyAiUsageResponse;
import com.waypoint.backend.model.entitlement.FeatureCode;
import com.waypoint.backend.service.ai.AiIntentService;
import com.waypoint.backend.service.ai.AiUsageService;
import com.waypoint.backend.service.ai.FamilyAiBudgetService;
import com.waypoint.backend.service.entitlement.EntitlementService;

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
    private final FamilyAiBudgetService familyAiBudgetService;
    private final EntitlementService entitlementService;

    public AiController(
            AiIntentService aiIntentService,
            AiUsageService aiUsageService,
            FamilyAiBudgetService familyAiBudgetService,
            EntitlementService entitlementService
    ) {
        this.aiIntentService = aiIntentService;
        this.aiUsageService = aiUsageService;
        this.familyAiBudgetService = familyAiBudgetService;
        this.entitlementService = entitlementService;
    }

    @GetMapping("/models")
    public AiModelCatalogResponse models() {
        return aiIntentService.models();
    }

    @GetMapping("/usage")
    public AiUsageResponse usage(@AuthenticationPrincipal UUID userId) {
        requireAiAccess(userId);
        return aiUsageService.current(userId);
    }

    @GetMapping("/family-usage")
    public FamilyAiUsageResponse familyUsage(@AuthenticationPrincipal UUID userId) {
        requireAiAccess(userId);
        return familyAiBudgetService.current(userId);
    }

    @PostMapping("/intent")
    public AiIntentResponse routeIntent(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AiIntentRequest request
    ) {
        requireAiAccess(userId);
        boolean familyRequest = familyAiBudgetService.beginRequest(
                userId,
                familyAiBudgetService.estimateInputTokens(request)
        );
        boolean completed = false;
        try {
            AiIntentResponse response = aiIntentService.route(request);
            completed = true;
            return response;
        } finally {
            if (familyRequest) familyAiBudgetService.finishRequest(completed);
        }
    }

    @PostMapping("/chat")
    public AiChatResponse chat(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AiChatRequest request
    ) {
        requireAiAccess(userId);
        boolean familyRequest = familyAiBudgetService.beginRequest(
                userId,
                familyAiBudgetService.estimateInputTokens(request)
        );
        boolean completed = false;
        try {
            AiChatResponse response = aiIntentService.chat(request);
            completed = true;
            return response;
        } finally {
            if (familyRequest) familyAiBudgetService.finishRequest(completed);
        }
    }

    private void requireAiAccess(UUID userId) {
        entitlementService.requireFeature(userId, FeatureCode.AI_SUMMARY);
    }
}
