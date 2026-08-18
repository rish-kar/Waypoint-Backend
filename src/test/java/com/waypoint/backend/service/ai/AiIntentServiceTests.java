package com.waypoint.backend.service.ai;

import com.waypoint.backend.model.ai.AiChatRequest;
import com.waypoint.backend.model.ai.AiChatResponse;
import com.waypoint.backend.model.ai.AiIntentRequest;
import com.waypoint.backend.model.ai.AiIntentResponse;
import com.waypoint.backend.utilities.client.ai.AiModelClient;
import com.waypoint.backend.utilities.exception.InvalidRequestException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiIntentServiceTests {

    @Test
    void correctsAccidentalCurrentTabScopeForNamedTargets() {
        AiIntentService service = service(response(
                "browser-action",
                "group-tabs",
                "current-tab",
                "repository tabs",
                List.of("repository", "repositories"),
                false,
                false
        ));

        AiIntentResponse result = service.route(request("self-hosted"));

        assertThat(result.scope()).isEqualTo("matching-tabs");
        assertThat(result.explicitCurrent()).isFalse();
        assertThat(result.matchTerms()).containsExactly("repository", "repositories");
    }

    @Test
    void blocksWholeWindowScopeUnlessItWasExplicit() {
        AiIntentService service = service(response(
                "browser-action",
                "close-tabs",
                "all-tabs",
                "all tabs",
                List.of(),
                false,
                false
        ));

        AiIntentResponse result = service.route(request("self-hosted"));

        assertThat(result.kind()).isEqualTo("clarification");
        assertThat(result.action()).isEqualTo("none");
        assertThat(result.clarification()).contains("explicitly ask for all tabs");
    }

    @Test
    void rejectsClientControlledUnknownModelIds() {
        AiIntentService service = service(response(
                "browser-action",
                "group-tabs",
                "matching-tabs",
                "tabs",
                List.of("tabs"),
                false,
                false
        ));

        assertThatThrownBy(() -> service.route(request("http://attacker.example/model")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unsupported AI model");
    }

    @Test
    void exposesOnlyServerConfiguredModelChoices() {
        AiIntentService service = service(response(
                "not-browser-action",
                "none",
                "none",
                "",
                List.of(),
                false,
                false
        ));

        assertThat(service.models().defaultModel()).isEqualTo("self-hosted");
        assertThat(service.models().models()).hasSize(1);
        assertThat(service.models().models().getFirst().enabled()).isTrue();
        assertThat(service.models().models().getFirst().label()).isEqualTo("Cloud AI");
    }

    private AiIntentRequest request(String model) {
        return new AiIntentRequest(
                "Group my repository tabs",
                false,
                "",
                "2026-08-18T18:00:00+05:30",
                "Asia/Kolkata",
                "en-IN",
                model
        );
    }

    private AiIntentService service(AiIntentResponse response) {
        return new AiIntentService(new StubClient(response));
    }

    private AiIntentResponse response(
            String kind,
            String action,
            String scope,
            String target,
            List<String> matchTerms,
            boolean explicitCurrent,
            boolean explicitAll
    ) {
        return new AiIntentResponse(
                kind,
                action,
                scope,
                target,
                matchTerms,
                List.of(),
                explicitCurrent,
                explicitAll,
                "Repository",
                "",
                "",
                "",
                "self-hosted"
        );
    }

    private static final class StubClient implements AiModelClient {
        private final AiIntentResponse response;

        private StubClient(AiIntentResponse response) {
            this.response = response;
        }

        @Override
        public AiIntentResponse route(AiIntentRequest request) {
            return response;
        }

        @Override
        public AiChatResponse chat(AiChatRequest request) {
            return new AiChatResponse("Test answer", "page", "self-hosted");
        }

        @Override
        public String modelId() {
            return "self-hosted";
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }
}
