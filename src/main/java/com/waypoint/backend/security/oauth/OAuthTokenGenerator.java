package com.waypoint.backend.security.oauth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class OAuthTokenGenerator {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String randomToken(int bytes) {
        if (bytes < 16) throw new IllegalArgumentException("OAuth token entropy is too small");
        byte[] value = new byte[bytes];
        SECURE_RANDOM.nextBytes(value);
        return URL_ENCODER.encodeToString(value);
    }

    public String sha256(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Value is required");
        return HexFormat.of().formatHex(digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
