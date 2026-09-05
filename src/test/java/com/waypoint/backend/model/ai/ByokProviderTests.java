package com.waypoint.backend.model.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ByokProviderTests {
    @Test
    void resolvesSupportedProvidersByStableId() {
        assertThat(ByokProvider.find("openai")).contains(ByokProvider.OPENAI);
        assertThat(ByokProvider.find("ANTHROPIC")).contains(ByokProvider.ANTHROPIC);
        assertThat(ByokProvider.find("google")).contains(ByokProvider.GOOGLE);
        assertThat(ByokProvider.find("xai")).contains(ByokProvider.XAI);
        assertThat(ByokProvider.find("openrouter")).contains(ByokProvider.OPENROUTER);
        assertThat(ByokProvider.find("unknown")).isEmpty();
    }

    @Test
    void filtersCatalogsToTextChatModels() {
        assertThat(ByokProvider.OPENAI.supportsModel("gpt-5.6-sol")).isTrue();
        assertThat(ByokProvider.OPENAI.supportsModel("gpt-image-1")).isFalse();
        assertThat(ByokProvider.ANTHROPIC.supportsModel("claude-sonnet-4-6")).isTrue();
        assertThat(ByokProvider.GOOGLE.supportsModel("gemini-3.8-flash")).isTrue();
        assertThat(ByokProvider.GOOGLE.supportsModel("gemini-3-pro-image-preview")).isFalse();
        assertThat(ByokProvider.XAI.supportsModel("grok-4.6")).isTrue();
        assertThat(ByokProvider.XAI.supportsModel("grok-imagine-image")).isFalse();
        assertThat(ByokProvider.OPENROUTER.supportsModel("anthropic/claude-sonnet-4.6")).isTrue();
    }
}
