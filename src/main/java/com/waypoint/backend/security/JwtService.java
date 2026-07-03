package com.waypoint.backend.security;

import com.waypoint.backend.common.UnauthorizedException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long expirationSeconds;
    private final ObjectMapper objectMapper;

    public JwtService(JwtProperties properties, ObjectMapper objectMapper) {
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes long");
        }
        if (properties.expirationSeconds() <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be positive");
        }
        this.secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = properties.expirationSeconds();
        this.objectMapper = objectMapper;
    }

    public String issueToken(UUID userId, String email) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = Map.of(
                "sub", userId.toString(),
                "email", email,
                "iat", now,
                "exp", now + expirationSeconds
        );
        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        return signingInput + "." + URL_ENCODER.encodeToString(hmac(signingInput));
    }

    public JwtClaims parseToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("Invalid token");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSignature = hmac(signingInput);
        byte[] actualSignature;
        try {
            actualSignature = URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Invalid token");
        }
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new UnauthorizedException("Invalid token");
        }
        try {
            JsonNode header = objectMapper.readTree(URL_DECODER.decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())) {
                throw new UnauthorizedException("Invalid token");
            }
            JsonNode payload = objectMapper.readTree(URL_DECODER.decode(parts[1]));
            long exp = payload.path("exp").asLong(0);
            if (exp <= Instant.now().getEpochSecond()) {
                throw new UnauthorizedException("Token has expired");
            }
            return new JwtClaims(UUID.fromString(payload.path("sub").asText()), payload.path("email").asText());
        } catch (IllegalArgumentException | JacksonException exception) {
            throw new UnauthorizedException("Invalid token");
        }
    }

    public long expirationSeconds() {
        return expirationSeconds;
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
