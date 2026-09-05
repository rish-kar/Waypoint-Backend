package com.waypoint.backend.controller.ai;

import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.service.admin.FamilyAiAdminService;
import com.waypoint.backend.service.ai.AiIntentService;
import com.waypoint.backend.service.ai.AiUsageService;
import com.waypoint.backend.service.ai.FamilyAiBudgetService;
import com.waypoint.backend.service.entitlement.EntitlementService;
import com.waypoint.backend.utilities.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AiControllerFamilyLimitTests {
    private AiIntentService aiIntentService;
    private FamilyAiBudgetService familyAiBudgetService;
    private AiController controller;
    private UUID userId;

    @BeforeEach
    void setUp() {
        aiIntentService = mock(AiIntentService.class);
        familyAiBudgetService = mock(FamilyAiBudgetService.class);
        controller = new AiController(
                aiIntentService,
                mock(AiUsageService.class),
                familyAiBudgetService,
                mock(FamilyAiAdminService.class),
                mock(EntitlementService.class)
        );
        userId = UUID.randomUUID();
    }

    @Test
    void fiveHourLimitStopsIntentBeforeProviderCall() {
        AiIntentRequest request = new AiIntentRequest("group my tabs", false, "", "", "", "", null);
        doThrow(new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "FAMILY_AI_SESSION_LIMIT_REACHED",
                "Your 5-hour Cloud AI limit has been reached. Try again after it resets."
        )).when(familyAiBudgetService).consumeRequestBudget(eq(userId), eq(request), eq(2), eq(800));

        assertThatThrownBy(() -> controller.routeIntent(userId, request))
                .isInstanceOf(ApiException.class);

        verify(aiIntentService, never()).route(any());
    }

    @Test
    void weeklyLimitStopsChatBeforeProviderCall() {
        AiChatRequest request = new AiChatRequest("Explain this", "Page", "", "Context", List.of(), null);
        doThrow(new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "FAMILY_AI_WEEKLY_LIMIT_REACHED",
                "Your weekly Cloud AI limit has been reached. Try again after it resets."
        )).when(familyAiBudgetService).consumeRequestBudget(eq(userId), eq(request), eq(4), eq(1_200));

        assertThatThrownBy(() -> controller.chat(userId, request))
                .isInstanceOf(ApiException.class);

        verify(aiIntentService, never()).chat(any());
    }
}
