package com.waypoint.backend.security.admin;

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
    private static final String PREFIX = "enc:v1:";
    private static final byte[] KEY_CONTEXT = "waypoint-admin-totp-v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminTotpSecretCipher(JwtProperties jwtProperties) {
        this.key = new SecretKeySpec(deriveKey(jwtProperties.secret()), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank() || isEncrypted(plaintext)) {
            return plaintext;
        }
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
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt admin TOTP secret", exception);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank() || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(stored.substring(PREFIX.length()));
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

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private byte[] deriveKey(String jwtSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KEY_CONTEXT);
            digest.update((byte) 0);
            digest.update(jwtSecret.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive admin TOTP encryption key", exception);
        }
    }
}
