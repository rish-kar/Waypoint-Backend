package com.waypoint.backend.security.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTokenGeneratorTests {
    @Test
    void generatesDistinctHighEntropyTokensAndStableHashes() {
        OAuthTokenGenerator generator = new OAuthTokenGenerator();
        String first = generator.randomToken(32);
        String second = generator.randomToken(32);
        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("=");
        assertThat(generator.sha256(first)).hasSize(64);
        assertThat(generator.sha256(first)).isEqualTo(generator.sha256(first));
        assertThat(generator.pkceChallenge(first)).isNotBlank().doesNotContain("=");
    }
}
