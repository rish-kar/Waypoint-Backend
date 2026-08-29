package com.waypoint.backend.service.ai;

import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.model.ai.AiModelCatalogResponse;
import com.waypoint.backend.model.ai.AiModelDescriptorResponse;
import com.waypoint.backend.utilities.client.ai.AiModelClient;
import com.waypoint.backend.utilities.exception.AiUnavailableException;
import com.waypoint.backend.utilities.exception.ExternalServiceException;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AiIntentService {
    private static final Set<String> KINDS = Set.of(
            "browser-action",
            "not-browser-action",
            "clarification",
            "unsupported-action"
    );
    private static final Set<String> ACTIONS = Set.of(
            "group-tabs",
            "ungroup-tabs",
            "close-duplicates",
            "close-tabs",
            "snooze-tabs",
            "save-workspace"
    );
    private static final Set<String> SCOPES = Set.of(
            "matching-tabs",
            "current-tab",
            "previous-selection",
            "all-tabs",
            "duplicates"
    );

    private final AiModelClient aiClient;

    public AiIntentService(AiModelClient aiClient) {
        this.aiClient = aiClient;
    }

    public AiIntentResponse route(AiIntentRequest request) {
        String requestedModel = normalizeModel(request.model());
        validateModel(requestedModel);
        return normalize(aiClient.route(request), request);
    }

    public AiChatResponse chat(AiChatRequest request) {
        String requestedModel = normalizeModel(request.model());
        validateModel(requestedModel);
        AiChatResponse response = aiClient.chat(request);
        if (response == null || !StringUtils.hasText(response.answer())) {
            throw new ExternalServiceException("Cloud AI returned an empty answer");
        }
        return response;
    }

    public AiModelCatalogResponse models() {
        AiModelDescriptorResponse configuredModel = new AiModelDescriptorResponse(
                aiClient.modelId(),
                "Cloud AI",
                aiClient.enabled(),
                "server"
        );
        return new AiModelCatalogResponse(aiClient.modelId(), List.of(configuredModel));
    }

    private void validateModel(String requestedModel) {
        if (!aiClient.modelId().equals(requestedModel)) {
            throw new InvalidRequestException("Unsupported AI model: " + requestedModel);
        }
        if (!aiClient.enabled()) {
            throw new AiUnavailableException("The selected AI model is not enabled");
        }
    }

    private AiIntentResponse normalize(AiIntentResponse raw, AiIntentRequest request) {
        String kind = lower(raw.kind());
        if (!KINDS.contains(kind)) {
            throw invalidModelOutput();
        }

        String modelId = StringUtils.hasText(raw.modelId()) ? raw.modelId().trim() : aiClient.modelId();
        String target = clean(raw.target());
        List<String> matchTerms = cleanList(raw.matchTerms());
        List<String> sites = cleanList(raw.sites());
        String clarification = clean(raw.clarification());

        if ("not-browser-action".equals(kind)) {
            return response(kind, "none", "none", target, matchTerms, sites, false, false, "", "", "", "", modelId);
        }
        if ("clarification".equals(kind)) {
            return clarification(target, clarification.isBlank() ? "Which open tabs should I use for that action?" : clarification, modelId);
        }
        if ("unsupported-action".equals(kind)) {
            String message = clarification.isBlank()
                    ? "That browser action is not supported yet."
                    : clarification;
            return response(kind, "none", "none", target, matchTerms, sites, false, false, "", "", "", message, modelId);
        }

        String action = lower(raw.action());
        String scope = lower(raw.scope());
        if (!ACTIONS.contains(action) || !SCOPES.contains(scope)) {
            throw invalidModelOutput();
        }

        boolean explicitCurrent = raw.explicitCurrent();
        boolean explicitAll = raw.explicitAll();
        if ("current-tab".equals(scope) && !explicitCurrent) {
            scope = "matching-tabs";
        }
        if ("previous-selection".equals(scope) && !request.lastSelectionAvailable()) {
            return clarification(target, "There is no previous tab selection to use. Which open tabs should I use?", modelId);
        }
        if ("all-tabs".equals(scope) && !explicitAll) {
            return clarification(target, "I won't apply that action to every open tab unless you explicitly ask for all tabs.", modelId);
        }
        if ("close-duplicates".equals(action)) {
            scope = "duplicates";
        }
        if ("duplicates".equals(scope) && !"close-duplicates".equals(action)) {
            return clarification(target, "Which open tabs should I use for that action?", modelId);
        }
        if ("matching-tabs".equals(scope) && target.isBlank() && matchTerms.isEmpty() && sites.isEmpty()) {
            return clarification(target, "Which open tabs should I use for that action?", modelId);
        }

        String groupTitle = clean(raw.groupTitle());
        String workspaceName = clean(raw.workspaceName());
        String wakeAt = clean(raw.wakeAt());
        if ("save-workspace".equals(action) && workspaceName.isBlank()) {
            return clarification(target, "What should I call the workspace?", modelId);
        }
        if ("snooze-tabs".equals(action) && !validFutureDateTime(wakeAt)) {
            return clarification(target, "When should those tabs come back?", modelId);
        }

        return response(
                kind,
                action,
                scope,
                target,
                matchTerms,
                sites,
                explicitCurrent,
                explicitAll,
                groupTitle,
                workspaceName,
                wakeAt,
                clarification,
                modelId
        );
    }

    private AiIntentResponse clarification(String target, String message, String modelId) {
        return response(
                "clarification",
                "none",
                "none",
                target,
                List.of(),
                List.of(),
                false,
                false,
                "",
                "",
                "",
                message,
                modelId
        );
    }

    private AiIntentResponse response(
            String kind,
            String action,
            String scope,
            String target,
            List<String> matchTerms,
            List<String> sites,
            boolean explicitCurrent,
            boolean explicitAll,
            String groupTitle,
            String workspaceName,
            String wakeAt,
            String clarification,
            String modelId
    ) {
        return new AiIntentResponse(
                kind,
                action,
                scope,
                target,
                matchTerms,
                sites,
                explicitCurrent,
                explicitAll,
                groupTitle,
                workspaceName,
                wakeAt,
                clarification,
                modelId
        );
    }

    private String normalizeModel(String model) {
        return StringUtils.hasText(model) ? model.trim().toLowerCase(Locale.ROOT) : aiClient.modelId();
    }

    private String lower(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                unique.add(value.trim());
            }
        }
        return List.copyOf(unique);
    }

    private boolean validFutureDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            return OffsetDateTime.parse(value).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private ExternalServiceException invalidModelOutput() {
        return new ExternalServiceException("Cloud AI returned an invalid intent");
    }
}
