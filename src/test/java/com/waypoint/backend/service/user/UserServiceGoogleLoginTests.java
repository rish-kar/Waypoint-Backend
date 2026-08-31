package com.waypoint.backend.service.user;

import com.waypoint.backend.model.auth.GoogleProfile;
import com.waypoint.backend.model.plan.PlanCode;
import com.waypoint.backend.model.plan.PlanEntity;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.repository.user.UserRepository;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceGoogleLoginTests {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlanService planService;
    @Mock
    private GoogleUserProvisioningService googleUserProvisioningService;
    @Mock
    private MicrosoftUserProvisioningService microsoftUserProvisioningService;
    @Mock
    private PlanEntity freePlan;

    @Test
    void reusesExistingGoogleAccountByVerifiedEmailWhenProviderSubjectChanged() {
        GoogleProfile profile = googleProfile("google-new-subject", "USER@Example.com");
        UserEntity existing = user("GOOGLE", "google-old-subject", "user@example.com");

        when(planService.require(PlanCode.FREE)).thenReturn(freePlan);
        when(userRepository.findByProviderAndProviderUserId(UserService.GOOGLE_PROVIDER, "google-new-subject"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(googleUserProvisioningService.updateLogin(existing, profile, "user@example.com", freePlan))
                .thenReturn(existing);

        UserEntity result = service().findOrCreateGoogleUser(profile);

        assertSame(existing, result);
        assertEquals(UserService.GOOGLE_PROVIDER, existing.getProvider());
        assertEquals("google-new-subject", existing.getProviderUserId());
        verify(googleUserProvisioningService, never()).create(profile, "user@example.com", freePlan);
        verify(planService).synchronizeUserPlan(existing);
    }

    @Test
    void rejectsCrossProviderEmailBeforeDatabaseInsert() {
        GoogleProfile profile = googleProfile("google-subject", "user@example.com");
        UserEntity existing = user("MICROSOFT", "microsoft-subject", "user@example.com");

        when(planService.require(PlanCode.FREE)).thenReturn(freePlan);
        when(userRepository.findByProviderAndProviderUserId(UserService.GOOGLE_PROVIDER, "google-subject"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        UnauthorizedException error = assertThrows(
                UnauthorizedException.class,
                () -> service().findOrCreateGoogleUser(profile)
        );

        assertEquals("Existing Waypoint account uses a different sign-in provider", error.getMessage());
        verify(googleUserProvisioningService, never()).create(profile, "user@example.com", freePlan);
        verify(googleUserProvisioningService, never()).updateLogin(existing, profile, "user@example.com", freePlan);
    }

    @Test
    void recoversWhenConcurrentGoogleInsertWinsUniqueEmailRace() {
        GoogleProfile profile = googleProfile("google-subject", "user@example.com");
        UserEntity existing = user("GOOGLE", "google-subject", "user@example.com");

        when(planService.require(PlanCode.FREE)).thenReturn(freePlan);
        when(userRepository.findByProviderAndProviderUserId(UserService.GOOGLE_PROVIDER, "google-subject"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(googleUserProvisioningService.create(profile, "user@example.com", freePlan))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));
        when(googleUserProvisioningService.updateLogin(existing, profile, "user@example.com", freePlan))
                .thenReturn(existing);

        UserEntity result = service().findOrCreateGoogleUser(profile);

        assertSame(existing, result);
        verify(planService).synchronizeUserPlan(existing);
    }

    private UserService service() {
        return new UserService(
                userRepository,
                planService,
                googleUserProvisioningService,
                microsoftUserProvisioningService
        );
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
        user.setDisplayName("User Name");
        return user;
    }
}
