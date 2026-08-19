package com.waypoint.backend.service.auth;

import com.waypoint.backend.config.auth.WaypointSessionProperties;
import com.waypoint.backend.model.auth.AuthResponse;
import com.waypoint.backend.model.auth.SessionResponse;
import com.waypoint.backend.model.auth.WaypointRefreshSessionEntity;
import com.waypoint.backend.model.entitlement.EntitlementResponse;
import com.waypoint.backend.model.user.UserEntity;
import com.waypoint.backend.model.user.UserResponse;
import com.waypoint.backend.repository.auth.WaypointRefreshSessionRepository;
import com.waypoint.backend.security.jwt.JwtService;
import com.waypoint.backend.security.oauth.OAuthTokenGenerator;
import com.waypoint.backend.service.entitlement.EntitlementService;
import com.waypoint.backend.service.plan.PlanService;
import com.waypoint.backend.service.user.UserService;
import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class WaypointSessionService {
    private final UserService userService;
    private final PlanService planService;
    private final JwtService jwtService;
    private final EntitlementService entitlementService;
    private final WaypointRefreshSessionRepository refreshSessionRepository;
    private final OAuthTokenGenerator tokenGenerator;
    private final WaypointSessionProperties properties;

    public WaypointSessionService(UserService userService,
                                  PlanService planService,
                                  JwtService jwtService,
                                  EntitlementService entitlementService,
                                  WaypointRefreshSessionRepository refreshSessionRepository,
                                  OAuthTokenGenerator tokenGenerator,
                                  WaypointSessionProperties properties) {
        this.userService = userService;
        this.planService = planService;
        this.jwtService = jwtService;
        this.entitlementService = entitlementService;
        this.refreshSessionRepository = refreshSessionRepository;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse issue(UUID userId) {
        return issueInternal(userId);
    }

    @Transactional
    public AuthResponse refresh(UUID userId, String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) throw invalidRefreshToken();
        WaypointRefreshSessionEntity session = refreshSessionRepository
                .findByRefreshTokenHash(tokenGenerator.sha256(rawRefreshToken))
                .orElseThrow(this::invalidRefreshToken);
        Instant now = Instant.now();
        if (!session.getUserId().equals(userId)
                || session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(now)) {
            throw invalidRefreshToken();
        }
        session.setRevokedAt(now);
        refreshSessionRepository.save(session);
        return issueInternal(userId);
    }

    public SessionResponse current(UUID userId) {
        if (userId == null) return SessionResponse.signedOut();
        UserEntity user = userService.requireById(userId);
        planService.synchronizeUserPlan(user);
        EntitlementResponse entitlement = entitlementService.currentEntitlement(userId, false);
        return new SessionResponse(true, UserResponse.from(user), entitlement);
    }

    @Transactional
    public void revokeAll(UUID userId) {
        if (userId != null) refreshSessionRepository.revokeAllForUser(userId, Instant.now());
    }

    @Scheduled(fixedDelayString = "${waypoint-session.cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        refreshSessionRepository.deleteByExpiresAtBefore(now);
        refreshSessionRepository.deleteByRevokedAtBefore(now.minusSeconds(86400));
    }

    private AuthResponse issueInternal(UUID userId) {
        UserEntity user = userService.requireById(userId);
        planService.synchronizeUserPlan(user);
        EntitlementResponse entitlement = entitlementService.currentEntitlement(userId, false);
        String accessToken = jwtService.issueToken(user.getId(), user.getEmail());
        String refreshToken = tokenGenerator.randomToken(48);
        WaypointRefreshSessionEntity refreshSession = new WaypointRefreshSessionEntity();
        refreshSession.setUserId(user.getId());
        refreshSession.setRefreshTokenHash(tokenGenerator.sha256(refreshToken));
        refreshSession.setExpiresAt(Instant.now().plusSeconds(properties.refreshTokenTtlSeconds()));
        refreshSessionRepository.save(refreshSession);
        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.expirationSeconds(),
                refreshToken,
                properties.refreshTokenTtlSeconds(),
                UserResponse.from(user),
                entitlement
        );
    }

    private UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException("Invalid, expired, or replayed Waypoint refresh token");
    }
}
