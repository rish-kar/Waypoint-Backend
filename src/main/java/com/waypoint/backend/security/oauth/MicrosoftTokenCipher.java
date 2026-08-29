package com.waypoint.backend.security.oauth;

import com.waypoint.backend.config.auth.MicrosoftOAuthProperties;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class MicrosoftTokenCipher {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String VERSION = "v1:";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public MicrosoftTokenCipher(MicrosoftOAuthProperties properties) {
        byte[] decoded = decodeKey(loadEncodedKey(properties));
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("Secret value is required");
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return VERSION + ENCODER.encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt Microsoft credential", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(VERSION)) {
            throw new IllegalStateException("Unsupported Microsoft credential ciphertext");
        }
        try {
            byte[] payload = DECODER.decode(ciphertext.substring(VERSION.length()));
            if (payload.length <= IV_BYTES) throw new IllegalStateException("Invalid Microsoft credential ciphertext");
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt Microsoft credential", exception);
        }
    }

    private String loadEncodedKey(MicrosoftOAuthProperties properties) {
        if (StringUtils.hasText(properties.tokenEncryptionKeyFile())) {
            try {
                return Files.readString(Path.of(properties.tokenEncryptionKeyFile()), StandardCharsets.UTF_8).trim();
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to read MICROSOFT_TOKEN_ENCRYPTION_KEY_FILE", exception);
            }
        }
        if (StringUtils.hasText(properties.tokenEncryptionKey())) {
            return properties.tokenEncryptionKey();
        }
        throw new IllegalStateException("A Microsoft token encryption key or managed key file is required");
    }

    private byte[] decodeKey(String encodedKey) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Microsoft token encryption key must be Base64 encoded", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("Microsoft token encryption key must decode to exactly 32 bytes");
        }
        return decoded;
    }
}
