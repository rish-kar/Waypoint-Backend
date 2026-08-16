package com.waypoint.backend.security.admin;

import com.waypoint.backend.config.admin.AdminProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

public class AdminTotpFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Admin-TOTP";
    private static final long TIME_STEP_SECONDS = 30L;
    private static final int CODE_MODULUS = 1_000_000;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final byte[] secret;

    public AdminTotpFilter(AdminProperties properties) {
        this.secret = decodeBase32(properties.totpSecret());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedCode = request.getHeader(HEADER);
        if (!validCode(suppliedCode, Instant.now())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"ADMIN_MFA_REQUIRED\",\"message\":\"Valid admin MFA code required\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    boolean validCode(String suppliedCode, Instant now) {
        if (!StringUtils.hasText(suppliedCode) || !suppliedCode.matches("\\d{6}")) {
            return false;
        }
        long counter = now.getEpochSecond() / TIME_STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            String expected = generateCode(counter + offset);
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    suppliedCode.getBytes(StandardCharsets.US_ASCII)
            )) {
                return true;
            }
        }
        return false;
    }

    private String generateCode(long counter) {
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
            throw new IllegalStateException("ADMIN_TOTP_SECRET must be configured for production admin MFA");
        }
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char character : normalized.toCharArray()) {
            int index = BASE32_ALPHABET.indexOf(character);
            if (index < 0) {
                throw new IllegalStateException("ADMIN_TOTP_SECRET must be valid Base32");
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
            throw new IllegalStateException("ADMIN_TOTP_SECRET is too short");
        }
        return decoded;
    }
}
