package com.waypoint.backend.security;

import com.waypoint.backend.common.UnauthorizedException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {
    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac";
    private static final Instant NOW = Instant.parse("2026-07-29T16:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void issuesAndParsesTokenWithRequiredClaims() {
        JwtService jwtService = service(SECRET, 300, NOW);
        UUID userId = UUID.randomUUID();

        String token = jwtService.issueToken(userId, "user@example.com");
        JwtClaims claims = jwtService.parseToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(token.split("\\.", -1)).hasSize(3);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService issuer = service(SECRET, 60, NOW);
        String token = issuer.issueToken(UUID.randomUUID(), "user@example.com");
        JwtService verifier = service(SECRET, 60, NOW.plusSeconds(61));

        assertThatThrownBy(() -> verifier.parseToken(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid or expired token");
    }

    @Test
    void rejectsTokenIssuedTooFarInFuture() {
        JwtService issuer = service(SECRET, 300, NOW.plusSeconds(120));
        String token = issuer.issueToken(UUID.randomUUID(), "user@example.com");
        JwtService verifier = service(SECRET, 300, NOW);

        assertThatThrownBy(() -> verifier.parseToken(token))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwtService = service(SECRET, 300, NOW);
        String token = jwtService.issueToken(UUID.randomUUID(), "user@example.com");
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'a' ? 'b' : 'a');

        assertThatThrownBy(() -> jwtService.parseToken(tampered))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = service(SECRET, 300, NOW);
        String token = issuer.issueToken(UUID.randomUUID(), "user@example.com");
        JwtService verifier = service("different-secret-that-is-also-long-enough", 300, NOW);

        assertThatThrownBy(() -> verifier.parseToken(token))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsMissingSubjectDataWhenIssuingToken() {
        JwtService jwtService = service(SECRET, 300, NOW);

        assertThatThrownBy(() -> jwtService.issueToken(null, "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtService.issueToken(UUID.randomUUID(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JwtService service(String secret, long expirationSeconds, Instant instant) {
        return new JwtService(
                new JwtProperties(secret, expirationSeconds),
                objectMapper,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }
}
