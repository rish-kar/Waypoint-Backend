package com.waypoint.backend.controller.ai;

import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.entitlement.FeatureCode;
import com.waypoint.backend.service.admin.FamilyAiAdminService;
import com.waypoint.backend.service.ai.AiIntentService;
import com.waypoint.backend.service.ai.AiUsageService;
import com.waypoint.backend.service.ai.ByokService;
import com.waypoint.backend.service.ai.FamilyAiBudgetService;
import com.waypoint.backend.service.entitlement.EntitlementService;
import com.waypoint.backend.utilities.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiControllerQuotaTests {
    private AiIntentService aiIntentService;
    private AiUsageService aiUsageService;
    private FamilyAiBudgetService familyAiBudgetService;
    private EntitlementService entitlementService;
    private AiController controller;
    private UUID userId;

    @BeforeEach
    void setUp() {
        aiIntentService = mock(AiIntentService.class);
        aiUsageService = mock(AiUsageService.class);
        familyAiBudgetService = mock(FamilyAiBudgetService.class);
        entitlementService = mock(EntitlementService.class);
        controller = new AiController(
                aiIntentService,
                aiUsageService,
                familyAiBudgetService,
                mock(FamilyAiAdminService.class),
                entitlementService,
                mock(ByokService.class)
        );
        userId = UUID.randomUUID();
    }

    @Test
    void intentConsumesOneUserQuotaUnitBeforeBudgetingAndProviderExecution() {
        AiIntentRequest request = new AiIntentRequest(
                "group my tabs",
                false,
                null,
                null,
                null,
                null,
                null
        );

        controller.routeIntent(userId, request);

        InOrder order = inOrder(entitlementService, aiUsageService, familyAiBudgetService, aiIntentService);
        order.verify(entitlementService).requireFeature(userId, FeatureCode.AI_SUMMARY);
        order.verify(aiUsageService).consume(userId);
        order.verify(familyAiBudgetService).consumeRequestBudget(userId, request, 2, 800);
        order.verify(aiIntentService).route(userId, request);
    }

    @Test
    void chatConsumesOneUserQuotaUnitBeforeBudgetingAndProviderExecution() {
        AiChatRequest request = new AiChatRequest(
                "What is this page about?",
                "Example",
                "Example description",
                "Readable page content for the Cloud AI request.",
                List.of(),
                null
        );

        controller.chat(userId, request);

        InOrder order = inOrder(entitlementService, aiUsageService, familyAiBudgetService, aiIntentService);
        order.verify(entitlementService).requireFeature(userId, FeatureCode.AI_SUMMARY);
        order.verify(aiUsageService).consume(userId);
        order.verify(familyAiBudgetService).consumeRequestBudget(userId, request, 4, 1_200);
        order.verify(aiIntentService).chat(userId, request);
    }

    @Test
    void exhaustedTrialStopsIntentBeforeBudgetingOrProviderExecution() {
        AiIntentRequest request = new AiIntentRequest(
                "group my tabs",
                false,
                null,
                null,
                null,
                null,
                null
        );
        ApiException exhausted = new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI_TRIAL_LIMIT_REACHED",
                "Your 20 Cloud AI trial requests have been used."
        );
        doThrow(exhausted).when(aiUsageService).consume(userId);

        assertThatThrownBy(() -> controller.routeIntent(userId, request)).isSameAs(exhausted);

        verifyNoInteractions(familyAiBudgetService, aiIntentService);
    }

    @Test
    void exhaustedTrialStopsChatBeforeBudgetingOrProviderExecution() {
        AiChatRequest request = new AiChatRequest(
                "What is this page about?",
                "Example",
                "Example description",
                "Readable page content for the Cloud AI request.",
                List.of(),
                null
        );
        ApiException exhausted = new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI_TRIAL_LIMIT_REACHED",
                "Your 20 Cloud AI trial requests have been used."
        );
        doThrow(exhausted).when(aiUsageService).consume(userId);

        assertThatThrownBy(() -> controller.chat(userId, request)).isSameAs(exhausted);

        verifyNoInteractions(familyAiBudgetService, aiIntentService);
    }
}
