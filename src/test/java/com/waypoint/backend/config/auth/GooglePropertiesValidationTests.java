package com.waypoint.backend.config.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GooglePropertiesValidationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "google.token-info-url=http://localhost/tokeninfo",
                    "google.user-info-url=http://localhost/userinfo"
            );

    @Test
    void failsFastWhenGoogleClientIdIsMissing() {
        contextRunner
                .withPropertyValues("google.client-id=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("google.client-id");
                });
    }

    @Test
    void startsWhenGoogleClientIdIsPresent() {
        contextRunner
                .withPropertyValues("google.client-id=expected-client")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GoogleProperties.class);
                });
    }

    @Configuration
    @EnableConfigurationProperties(GoogleProperties.class)
    static class TestConfig {
    }
}
