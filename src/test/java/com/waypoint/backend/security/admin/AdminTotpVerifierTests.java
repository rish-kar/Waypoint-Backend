package com.waypoint.backend.security.admin;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTotpVerifierTests {
    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final long PERIOD_SECONDS = 30L;

    private final AdminTotpVerifier verifier = new AdminTotpVerifier();

    @Test
    void acceptsCurrentMicrosoftAuthenticatorStyleCode() throws Exception {
        Instant now = Instant.ofEpochSecond(1_700_000_015L);
        String code = codeForCounter(now.getEpochSecond() / PERIOD_SECONDS);

        assertThat(verifier.validCode(SECRET, code, now)).isTrue();
    }

    @Test
    void acceptsCodeGeneratedWithinFiveMinuteWindow() throws Exception {
        Instant now = Instant.ofEpochSecond(1_700_000_015L);
        long currentCounter = now.getEpochSecond() / PERIOD_SECONDS;
        String code = codeForCounter(currentCounter - 9L);

        assertThat(verifier.validCode(SECRET, code, now)).isTrue();
    }

    @Test
    void rejectsCodeAtFiveMinutesOrOlder() throws Exception {
        long currentCounter = 60_000_000L;
        Instant now = Instant.ofEpochSecond(currentCounter * PERIOD_SECONDS);
        String code = codeForCounter(currentCounter - 10L);

        assertThat(verifier.validCode(SECRET, code, now)).isFalse();
    }

    @Test
    void acceptsOneFutureStepForClockSkew() throws Exception {
        Instant now = Instant.ofEpochSecond(1_700_000_015L);
        long currentCounter = now.getEpochSecond() / PERIOD_SECONDS;
        String code = codeForCounter(currentCounter + 1L);

        assertThat(verifier.validCode(SECRET, code, now)).isTrue();
    }

    private String codeForCounter(long counter) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(decodeBase32(SECRET), "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
    }

    private byte[] decodeBase32(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char character : value.toCharArray()) {
            int index = alphabet.indexOf(character);
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }
}
