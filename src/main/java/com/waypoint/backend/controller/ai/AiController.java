package com.waypoint.backend.controller.ai;

import com.waypoint.backend.model.admin.AdminFamilyAiUsageResponse;
import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.model.ai.AiModelCatalogResponse;
import com.waypoint.backend.model.ai.AiUsageResponse;
import com.waypoint.backend.model.ai.ByokApiKeyRequest;
import com.waypoint.backend.model.ai.ByokModelCatalogResponse;
import com.waypoint.backend.model.ai.ByokModelRequest;
import com.waypoint.backend.model.ai.ByokStatusResponse;
import com.waypoint.backend.model.ai.FamilyAiUsageResponse;
import com.waypoint.backend.model.entitlement.FeatureCode;
import com.waypoint.backend.service.admin.FamilyAiAdminService;
import com.waypoint.backend.service.ai.AiIntentService;
import com.waypoint.backend.service.ai.AiUsageService;
import com.waypoint.backend.service.ai.ByokService;
import com.waypoint.backend.service.ai.FamilyAiBudgetService;
import com.waypoint.backend.service.entitlement.EntitlementService;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final FamilyAiAdminService familyAiAdminService;
    private final EntitlementService entitlementService;
    private final ByokService byokService;

    public AiController(
            AiIntentService aiIntentService,
            AiUsageService aiUsageService,
            FamilyAiBudgetService familyAiBudgetService,
            FamilyAiAdminService familyAiAdminService,
            EntitlementService entitlementService,
            ByokService byokService
    ) {
        this.aiIntentService = aiIntentService;
        this.aiUsageService = aiUsageService;
        this.familyAiBudgetService = familyAiBudgetService;
        this.familyAiAdminService = familyAiAdminService;
        this.entitlementService = entitlementService;
        this.byokService = byokService;
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
        return familyAiBudgetService.current(userId);
    }

    @GetMapping("/family-admin-usage")
    public AdminFamilyAiUsageResponse familyAdminUsage(@AuthenticationPrincipal UUID userId) {
        return familyAiAdminService.currentForAuthenticatedAdmin(userId);
    }

    @GetMapping("/byok")
    public ByokStatusResponse byokStatus(@AuthenticationPrincipal UUID userId) {
        return byokService.status(userId);
    }

    @PutMapping("/byok/key")
    public ByokModelCatalogResponse saveByokKey(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ByokApiKeyRequest request
    ) {
        return byokService.saveApiKey(userId, request.provider(), request.apiKey());
    }

    @GetMapping("/byok/models")
    public ByokModelCatalogResponse byokModels(@AuthenticationPrincipal UUID userId) {
        return byokService.models(userId);
    }

    @PatchMapping("/byok/model")
    public ByokStatusResponse selectByokModel(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ByokModelRequest request
    ) {
        return byokService.selectModel(userId, request.model());
    }

    @DeleteMapping("/byok")
    public ByokStatusResponse removeByok(@AuthenticationPrincipal UUID userId) {
        return byokService.remove(userId);
    }

    @PostMapping("/intent")
    public AiIntentResponse routeIntent(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AiIntentRequest request
    ) {
        requireAiAccess(userId);
        familyAiBudgetService.consumeRequestBudget(userId, request, 2, 800);
        return aiIntentService.route(userId, request);
    }

    @PostMapping("/chat")
    public AiChatResponse chat(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AiChatRequest request
    ) {
        requireAiAccess(userId);
        familyAiBudgetService.consumeRequestBudget(userId, request, 4, 1_200);
        return aiIntentService.chat(userId, request);
    }

    private void requireAiAccess(UUID userId) {
        entitlementService.requireFeature(userId, FeatureCode.AI_SUMMARY);
    }
}
