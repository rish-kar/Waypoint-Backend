package com.waypoint.backend.service.user;

import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:google-email-collision;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "subscription-access.admin-email=admin@example.com"
})
@ActiveProfiles("test")
class UserServiceGoogleEmailCollisionIntegrationTests {
    private final UserService userService;
    private final UserRepository userRepository;

    @Autowired
    UserServiceGoogleEmailCollisionIntegrationTests(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void existingGoogleEmailIsReusedWithinGoogleProvider() {
        UserEntity existing = userRepository.save(user(
                UserService.GOOGLE_PROVIDER,
                "old-google-subject",
                "user@example.com"
        ));

        UserEntity authenticated = userService.findOrCreateGoogleUser(googleProfile(
                "new-google-subject",
                "USER@Example.com"
        ));

        assertThat(authenticated.getId()).isEqualTo(existing.getId());
        assertThat(authenticated.getProvider()).isEqualTo(UserService.GOOGLE_PROVIDER);
        assertThat(authenticated.getProviderUserId()).isEqualTo("new-google-subject");
        assertThat(authenticated.getEmail()).isEqualTo("user@example.com");
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void configuredAdminGoogleLoginReusesAccountAndSynchronizesAdminPlan() {
        UserEntity existing = userRepository.save(user(
                UserService.GOOGLE_PROVIDER,
                "old-admin-google-subject",
                "admin@example.com"
        ));

        UserEntity authenticated = userService.findOrCreateGoogleUser(googleProfile(
                "new-admin-google-subject",
                "ADMIN@Example.com"
        ));

        assertThat(authenticated.getId()).isEqualTo(existing.getId());
        assertThat(authenticated.getProviderUserId()).isEqualTo("new-admin-google-subject");
        assertThat(authenticated.getPlan()).isNotNull();
        assertThat(authenticated.getPlan().getCode()).isEqualTo(PlanCode.ADMIN);
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void googleAndMicrosoftCanUseSameEmailAsSeparateAccounts() {
        UserEntity microsoft = userRepository.save(user(
                UserService.MICROSOFT_PROVIDER,
                "microsoft-subject",
                "user@example.com"
        ));

        UserEntity google = userService.findOrCreateGoogleUser(googleProfile(
                "google-subject",
                "user@example.com"
        ));

        assertThat(google.getId()).isNotEqualTo(microsoft.getId());
        assertThat(google.getProvider()).isEqualTo(UserService.GOOGLE_PROVIDER);
        assertThat(microsoft.getProvider()).isEqualTo(UserService.MICROSOFT_PROVIDER);
        assertThat(google.getEmail()).isEqualTo("user@example.com");
        assertThat(microsoft.getEmail()).isEqualTo("user@example.com");
        assertThat(userRepository.findAll()).hasSize(2);
        assertThat(userRepository.findByEmailAndProvider("user@example.com", UserService.GOOGLE_PROVIDER))
                .contains(google);
        assertThat(userRepository.findByEmailAndProvider("user@example.com", UserService.MICROSOFT_PROVIDER))
                .contains(microsoft);
    }

    private GoogleProfile googleProfile(String providerUserId, String email) {
        return new GoogleProfile(
                providerUserId,
                email,
                true,
                "User Name",
                "https://example.com/picture.png",
                "test-google-client"
        );
    }

    private UserEntity user(String provider, String providerUserId, String email) {
        UserEntity user = new UserEntity();
        user.setProvider(provider);
        user.setProviderUserId(providerUserId);
        user.setEmail(email);
        user.setDisplayName("Existing User");
        user.setPictureUrl("https://example.com/existing.png");
        return user;
    }
}
