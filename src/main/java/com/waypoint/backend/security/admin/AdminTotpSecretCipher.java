package com.waypoint.backend.security.admin;

import com.waypoint.backend.config.admin.AdminProperties;
import com.waypoint.backend.security.jwt.JwtProperties;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AdminTotpSecretCipher {
    private static final String CURRENT_PREFIX = "enc:v2:";
    private static final String LEGACY_PREFIX = "enc:v1:";
    private static final byte[] CURRENT_KEY_CONTEXT = "waypoint-admin-totp-v2".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LEGACY_KEY_CONTEXT = "waypoint-admin-totp-v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec currentKey;
    private final SecretKeySpec legacyKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminTotpSecretCipher(AdminProperties adminProperties, JwtProperties jwtProperties) {
        this.currentKey = new SecretKeySpec(
                deriveKey(CURRENT_KEY_CONTEXT, adminProperties.totpEncryptionKey()),
                "AES"
        );
        this.legacyKey = new SecretKeySpec(
                deriveKey(LEGACY_KEY_CONTEXT, jwtProperties.secret()),
                "AES"
        );
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank() || value.startsWith(CURRENT_PREFIX)) {
            return value;
        }
        String plaintext = value.startsWith(LEGACY_PREFIX) ? decryptWithKey(value, LEGACY_PREFIX, legacyKey) : value;
        return encryptWithKey(plaintext, CURRENT_PREFIX, currentKey);
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (stored.startsWith(CURRENT_PREFIX)) {
            return decryptWithKey(stored, CURRENT_PREFIX, currentKey);
        }
        if (stored.startsWith(LEGACY_PREFIX)) {
            return decryptWithKey(stored, LEGACY_PREFIX, legacyKey);
        }
        return stored;
    }

    public boolean isEncrypted(String value) {
        return value != null && (value.startsWith(CURRENT_PREFIX) || value.startsWith(LEGACY_PREFIX));
    }

    public boolean needsReencryption(String value) {
        return value != null && !value.isBlank() && !value.startsWith(CURRENT_PREFIX);
    }

    private String encryptWithKey(String plaintext, String prefix, SecretKeySpec key) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt admin TOTP secret", exception);
        }
    }

    private String decryptWithKey(String stored, String prefix, SecretKeySpec key) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(stored.substring(prefix.length()));
            if (payload.length <= IV_BYTES) {
                throw new IllegalStateException("Encrypted admin TOTP secret is invalid");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt admin TOTP secret", exception);
        }
    }

    private byte[] deriveKey(byte[] context, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(context);
            digest.update((byte) 0);
            digest.update(secret.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive admin TOTP encryption key", exception);
        }
    }
}
