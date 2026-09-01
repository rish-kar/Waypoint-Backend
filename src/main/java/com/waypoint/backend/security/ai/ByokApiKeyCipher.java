package com.waypoint.backend.security.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ByokApiKeyCipher {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String VERSION = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ByokApiKeyCipher(@Value("${AI_BYOK_ENCRYPTION_KEY:}") String encodedKey) {
        this.encodedKey = encodedKey == null ? "" : encodedKey.trim();
    }

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.trim().getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return VERSION + ENCODER.encodeToString(payload);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt OpenAI API key", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (!StringUtils.hasText(ciphertext) || !ciphertext.startsWith(VERSION)) {
            throw new IllegalStateException("Unsupported OpenAI API key ciphertext");
        }
        try {
            byte[] payload = DECODER.decode(ciphertext.substring(VERSION.length()));
            if (payload.length <= IV_BYTES) {
                throw new IllegalStateException("Invalid OpenAI API key ciphertext");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt OpenAI API key", exception);
        }
    }

    private SecretKeySpec key() {
        if (!StringUtils.hasText(encodedKey)) {
            throw new IllegalStateException("AI_BYOK_ENCRYPTION_KEY is required before BYOK can be configured");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AI_BYOK_ENCRYPTION_KEY must be Base64 encoded", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("AI_BYOK_ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
