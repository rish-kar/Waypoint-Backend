package com.waypoint.backend.security.jwt;

import com.waypoint.backend.utilities.exception.UnauthorizedException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuedAtValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {
    private static final String ISSUER = "waypoint-backend";
    private static final String AUDIENCE = "waypoint-extension";
    private static final long CLOCK_SKEW_SECONDS = 30;
    private static final int MAX_TOKEN_LENGTH = 4096;

    private final long expirationSeconds;
    private final Clock clock;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    @Autowired
    public JwtService(JwtProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, ObjectMapper objectMapper, Clock clock) {
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes long");
        }
        if (properties.expirationSeconds() <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be positive");
        }
        this.expirationSeconds = properties.expirationSeconds();
        this.clock = clock;

        SecretKey secretKey = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        this.jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .validateType(true)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(CLOCK_SKEW_SECONDS));
        timestampValidator.setClock(clock);
        timestampValidator.setAllowEmptyExpiryClaim(false);
        timestampValidator.setAllowEmptyNotBeforeClaim(false);
        JwtIssuedAtValidator issuedAtValidator = new JwtIssuedAtValidator(true);
        issuedAtValidator.setClock(clock);
        issuedAtValidator.setClockSkew(Duration.ofSeconds(CLOCK_SKEW_SECONDS));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuedAtValidator,
                new JwtIssuerValidator(ISSUER)
        ));
        this.jwtDecoder = decoder;
    }

    public String issueToken(UUID userId, String email) {
        if (userId == null || email == null || email.isBlank()) {
            throw new IllegalArgumentException("JWT subject and email are required");
        }

        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(userId.toString())
                .claim("email", email)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    public JwtClaims parseToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw invalidToken();
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            String tokenId = jwt.getId();
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            Instant now = clock.instant();

            if (!jwt.getAudience().contains(AUDIENCE)
                    || subject == null || subject.isBlank()
                    || email == null || email.isBlank()
                    || tokenId == null || tokenId.isBlank()
                    || issuedAt == null
                    || expiresAt == null
                    || issuedAt.isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))
                    || !expiresAt.isAfter(now)
                    || !expiresAt.isAfter(issuedAt)
                    || Duration.between(issuedAt, expiresAt).getSeconds() > expirationSeconds + CLOCK_SKEW_SECONDS) {
                throw invalidToken();
            }

            return new JwtClaims(
                    UUID.fromString(subject),
                    email,
                    UUID.fromString(tokenId),
                    expiresAt
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    private UnauthorizedException invalidToken() {
        return new UnauthorizedException("Invalid or expired token");
    }
}