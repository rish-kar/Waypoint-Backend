package com.waypoint.backend.user;

import com.waypoint.backend.auth.GoogleProfile;
import com.waypoint.backend.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    public static final String GOOGLE_PROVIDER = "GOOGLE";

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity findOrCreateGoogleUser(GoogleProfile profile) {
        String normalizedEmail = normalizeEmail(profile.email());
        UserEntity user = userRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, profile.providerUserId())
                .orElseGet(() -> {
                    UserEntity created = new UserEntity();
                    created.setProvider(GOOGLE_PROVIDER);
                    created.setProviderUserId(profile.providerUserId());
                    created.setCreatedAt(Instant.now());
                    return created;
                });

        user.setEmail(normalizedEmail);
        user.setDisplayName(profile.displayName());
        user.setPictureUrl(profile.pictureUrl());
        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserEntity requireById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
