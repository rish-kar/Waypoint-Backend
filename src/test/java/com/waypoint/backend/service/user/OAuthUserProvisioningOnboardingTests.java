package com.waypoint.backend.service.user;

import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.model.auth.MicrosoftProfile;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthUserProvisioningOnboardingTests {

    @Test
    void newGoogleUserRequiresOnboarding() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity user = new GoogleUserProvisioningService(repository).create(
                new GoogleProfile(
                        "google-subject",
                        "user@example.com",
                        true,
                        "User",
                        "https://example.com/avatar.png",
                        "client"
                ),
                "user@example.com",
                new PlanEntity()
        );

        assertThat(user.isOnboardingCompleted()).isFalse();
    }

    @Test
    void newMicrosoftUserRequiresOnboarding() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity user = new MicrosoftUserProvisioningService(repository).create(
                new MicrosoftProfile("microsoft-subject", "user@example.com", "User"),
                "user@example.com",
                new PlanEntity()
        );

        assertThat(user.isOnboardingCompleted()).isFalse();
    }
}
