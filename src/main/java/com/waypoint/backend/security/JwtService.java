package com.waypoint.backend.security;

import com.waypoint.backend.common.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_TYPE = "JWT";
    private static final String ISSUER = "waypoint-backend";
    private static final String AUDIENCE = "waypoint-extension";
    private static final long CLOCK_SKEW_SECONDS = 30;
    private static final int MAX_TOKEN_LENGTH = 4096;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long expirationSeconds;
    private final ObjectMapper objectMapper;
    private final Clock clock;

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
        this.secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = properties.expirationSeconds();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String issueToken(UUID userId, String email) {
        if (userId == null || email == null || email.isBlank()) {
            throw new IllegalArgumentException("JWT subject and email are required");
        }

        long now = clock.instant().getEpochSecond();
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", TOKEN_TYPE
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", ISSUER);
        payload.put("aud", AUDIENCE);
        payload.put("sub", userId.toString());
        payload.put("email", email);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", now);
        payload.put("nbf", now);
        payload.put("exp", now + expirationSeconds);

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        return signingInput + "." + URL_ENCODER.encodeToString(hmac(signingInput));
    }

    public JwtClaims parseToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw invalidToken();
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw invalidToken();
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] actualSignature;
        try {
            actualSignature = URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }

        if (!MessageDigest.isEqual(hmac(signingInput), actualSignature)) {
            throw invalidToken();
        }

        try {
            JsonNode header = objectMapper.readTree(URL_DECODER.decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())
                    || !TOKEN_TYPE.equals(header.path("typ").asText())) {
                throw invalidToken();
            }

            JsonNode payload = objectMapper.readTree(URL_DECODER.decode(parts[1]));
            String issuer = payload.path("iss").asText();
            String audience = payload.path("aud").asText();
            String subject = payload.path("sub").asText();
            String email = payload.path("email").asText();
            String tokenId = payload.path("jti").asText();
            long issuedAt = payload.path("iat").asLong(0);
            long notBefore = payload.path("nbf").asLong(0);
            long expiresAt = payload.path("exp").asLong(0);
            long now = clock.instant().getEpochSecond();

            if (!ISSUER.equals(issuer)
                    || !AUDIENCE.equals(audience)
                    || subject.isBlank()
                    || email.isBlank()
                    || tokenId.isBlank()
                    || issuedAt <= 0
                    || notBefore <= 0
                    || expiresAt <= 0
                    || issuedAt > now + CLOCK_SKEW_SECONDS
                    || notBefore > now + CLOCK_SKEW_SECONDS
                    || expiresAt <= now
                    || expiresAt <= issuedAt
                    || expiresAt - issuedAt > expirationSeconds + CLOCK_SKEW_SECONDS) {
                throw invalidToken();
            }

            UUID.fromString(tokenId);
            return new JwtClaims(UUID.fromString(subject), email);
        } catch (IllegalArgumentException | JacksonException exception) {
            throw invalidToken();
        }
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    private UnauthorizedException invalidToken() {
        return new UnauthorizedException("Invalid or expired token");
    }

    private String encodeJson(Object value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to create token");
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign token");
        }
    }
}
