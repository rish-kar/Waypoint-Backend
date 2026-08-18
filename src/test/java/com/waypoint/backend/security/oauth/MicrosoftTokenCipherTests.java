package com.waypoint.backend.security.oauth;

import com.waypoint.backend.config.auth.MicrosoftOAuthProperties;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicrosoftTokenCipherTests {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void encryptsAndDecryptsWithoutPersistingPlaintext() {
        MicrosoftTokenCipher cipher = new MicrosoftTokenCipher(properties(KEY));
        String encrypted = cipher.encrypt("refresh-token-secret");
        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain("refresh-token-secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("refresh-token-secret");
    }

    @Test
    void rejectsWrongSizedKeys() {
        assertThatThrownBy(() -> new MicrosoftTokenCipher(properties("YWJj")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    private MicrosoftOAuthProperties properties(String key) {
        return new MicrosoftOAuthProperties("client", "secret", "common",
                "http://localhost:8080/api/v1/auth/microsoft/callback", "https://graph.microsoft.com/v1.0/me",
                key, List.of("https://test-extension.chromiumapp.org/microsoft"), 600, 180);
    }
}
