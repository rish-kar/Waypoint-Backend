package com.waypoint.backend.security.admin;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

@Component
public class AdminTotpVerifier {
    private static final long TIME_STEP_SECONDS = 30L;
    private static final int CODE_MODULUS = 1_000_000;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public boolean validCode(String secretValue, String suppliedCode, Instant now) {
        if (!StringUtils.hasText(suppliedCode) || !suppliedCode.matches("\\d{6}")) {
            return false;
        }
        byte[] secret = decodeBase32(secretValue);
        long counter = now.getEpochSecond() / TIME_STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            String expected = generateCode(secret, counter + offset);
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    suppliedCode.getBytes(StandardCharsets.US_ASCII)
            )) {
                return true;
            }
        }
        return false;
    }

    public void validateSecret(String secretValue) {
        decodeBase32(secretValue);
    }

    private String generateCode(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % CODE_MODULUS);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify admin MFA code", exception);
        }
    }

    private byte[] decodeBase32(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Admin TOTP secret is required");
        }
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char character : normalized.toCharArray()) {
            int index = BASE32_ALPHABET.indexOf(character);
            if (index < 0) {
                throw new IllegalArgumentException("Admin TOTP secret must be valid Base32");
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        byte[] decoded = output.toByteArray();
        if (decoded.length < 10) {
            throw new IllegalArgumentException("Admin TOTP secret is too short");
        }
        return decoded;
    }
}