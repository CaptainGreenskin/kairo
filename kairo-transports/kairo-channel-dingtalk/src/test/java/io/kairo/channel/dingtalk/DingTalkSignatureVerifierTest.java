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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DingTalkSignatureVerifierTest {

    private static final String SECRET = "SEC_test_signing_secret_v0.9.1";

    @Test
    void validSignatureWithinWindow_isAccepted() {
        long now = 1_700_000_000_000L;
        DingTalkSignatureVerifier v =
                new DingTalkSignatureVerifier(
                        SECRET,
                        Duration.ofHours(1),
                        Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));

        String expected = v.sign(now);
        assertThat(v.verify(now, expected)).isTrue();
    }

    @Test
    void tamperedSignature_isRejected() {
        long now = 1_700_000_000_000L;
        DingTalkSignatureVerifier v =
                new DingTalkSignatureVerifier(
                        SECRET,
                        Duration.ofHours(1),
                        Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));

        String tampered = v.sign(now).substring(0, 10) + "AAAAAAAAAAAAAAAAAAAAAA==";
        assertThat(v.verify(now, tampered)).isFalse();
    }

    @Test
    void timestampOutsideReplayWindow_isRejected() {
        long now = 1_700_000_000_000L;
        DingTalkSignatureVerifier v =
                new DingTalkSignatureVerifier(
                        SECRET,
                        Duration.ofHours(1),
                        Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));

        long stale = now - Duration.ofHours(2).toMillis();
        String validForStaleTs = v.sign(stale);

        assertThat(v.verify(stale, validForStaleTs)).isFalse();
    }

    @Test
    void blankSigningSecret_isRejected() {
        assertThatThrownBy(() -> new DingTalkSignatureVerifier("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signingSecret");
    }

    @Test
    void zeroReplayWindow_isRejected() {
        assertThatThrownBy(
                        () ->
                                new DingTalkSignatureVerifier(
                                        SECRET, Duration.ZERO, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replayWindow");
    }
}
