/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.channel.dingtalk;

import io.kairo.api.Experimental;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies DingTalk custom-bot webhook signatures. DingTalk computes the signature as {@code
 * base64(HMAC-SHA256(key=signingSecret, message="timestamp\nsigningSecret"))} and sends it
 * alongside the webhook request as query parameters {@code timestamp} and {@code sign}.
 *
 * <p>Rejects timestamps outside {@code replayWindow} (default 1 hour) to bound replay attacks.
 *
 * @since v0.9.1 (Experimental)
 */
@Experimental("DingTalk signature verifier — contract may change in v0.10")
public final class DingTalkSignatureVerifier {

    private static final Duration DEFAULT_REPLAY_WINDOW = Duration.ofHours(1);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final String signingSecret;
    private final Duration replayWindow;
    private final Clock clock;

    public DingTalkSignatureVerifier(String signingSecret) {
        this(signingSecret, DEFAULT_REPLAY_WINDOW, Clock.systemUTC());
    }

    public DingTalkSignatureVerifier(String signingSecret, Duration replayWindow, Clock clock) {
        this.signingSecret = Objects.requireNonNull(signingSecret, "signingSecret");
        if (signingSecret.isBlank()) {
            throw new IllegalArgumentException("signingSecret must not be blank");
        }
        this.replayWindow = Objects.requireNonNull(replayWindow, "replayWindow");
        if (replayWindow.isNegative() || replayWindow.isZero()) {
            throw new IllegalArgumentException("replayWindow must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Verify {@code receivedSignature} against {@code timestamp}. Returns {@code true} iff the
     * signature matches the DingTalk formula AND the timestamp is within {@link #replayWindow} of
     * the current clock.
     *
     * @param timestamp millisecond epoch timestamp received on the webhook request
     * @param receivedSignature base64-encoded HMAC-SHA256 from the webhook request
     */
    public boolean verify(long timestamp, String receivedSignature) {
        Objects.requireNonNull(receivedSignature, "receivedSignature");
        long now = clock.millis();
        long skew = Math.abs(now - timestamp);
        if (skew > replayWindow.toMillis()) {
            return false;
        }
        String expected = sign(timestamp);
        return constantTimeEquals(expected, receivedSignature);
    }

    /**
     * Compute the expected signature for {@code timestamp}. Exposed so callers can stamp outbound
     * requests with the same formula DingTalk expects.
     */
    public String sign(long timestamp) {
        String stringToSign = timestamp + "\n" + signingSecret;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("DingTalk HMAC-SHA256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < aBytes.length; i++) {
            diff |= aBytes[i] ^ bBytes[i];
        }
        return diff == 0;
    }
}
