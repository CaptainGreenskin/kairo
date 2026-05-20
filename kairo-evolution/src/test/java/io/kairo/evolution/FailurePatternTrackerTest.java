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
package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.evolution.FailurePatternTracker.FailurePattern;
import io.kairo.evolution.FailurePatternTracker.FailureSignature;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class FailurePatternTrackerTest {

    @Test
    void recordBelowThresholdReturnsEmpty() {
        FailurePatternTracker tracker = new FailurePatternTracker(3, Duration.ofDays(30));
        FailureSignature sig = new FailureSignature("IOException", "bash", "Permission denied");

        assertThat(tracker.record(sig)).isEmpty();
        assertThat(tracker.record(sig)).isEmpty();
    }

    @Test
    void recordAtThresholdReturnsPattern() {
        FailurePatternTracker tracker = new FailurePatternTracker(3, Duration.ofDays(30));
        FailureSignature sig = new FailureSignature("IOException", "bash", "Permission denied");

        tracker.record(sig);
        tracker.record(sig);
        Optional<FailurePattern> result = tracker.record(sig);

        assertThat(result).isPresent();
        assertThat(result.get().signature()).isEqualTo(sig);
        assertThat(result.get().occurrences()).hasSize(3);
        assertThat(result.get().thresholdReached()).isEqualTo(3);
    }

    @Test
    void patternResetAfterThreshold() {
        FailurePatternTracker tracker = new FailurePatternTracker(2, Duration.ofDays(30));
        FailureSignature sig = new FailureSignature("NPE", "tool", "null ref");

        tracker.record(sig);
        assertThat(tracker.record(sig)).isPresent();

        assertThat(tracker.record(sig)).isEmpty();
        assertThat(tracker.record(sig)).isPresent();
    }

    @Test
    void differentSignaturesTrackedSeparately() {
        FailurePatternTracker tracker = new FailurePatternTracker(2, Duration.ofDays(30));
        FailureSignature sigA = new FailureSignature("IOException", "bash", "File not found");
        FailureSignature sigB = new FailureSignature("TimeoutException", "http", "Read timed out");

        tracker.record(sigA);
        tracker.record(sigB);

        assertThat(tracker.record(sigA)).isPresent();
        assertThat(tracker.countWithinWindow(sigB)).isEqualTo(1);
    }

    @Test
    void countWithinWindowReturnsCorrectCount() {
        FailurePatternTracker tracker = new FailurePatternTracker(5, Duration.ofDays(30));
        FailureSignature sig = new FailureSignature("Error", "tool", "msg");

        tracker.record(sig);
        tracker.record(sig);
        tracker.record(sig);

        assertThat(tracker.countWithinWindow(sig)).isEqualTo(3);
    }

    @Test
    void countWithinWindowReturnsZeroForUnknown() {
        FailurePatternTracker tracker = new FailurePatternTracker();
        FailureSignature sig = new FailureSignature("Error", "tool", "msg");
        assertThat(tracker.countWithinWindow(sig)).isZero();
    }

    @Test
    void activeSignaturesReturnsAll() {
        FailurePatternTracker tracker = new FailurePatternTracker(10, Duration.ofDays(30));
        FailureSignature a = new FailureSignature("A", "tool", "msg");
        FailureSignature b = new FailureSignature("B", "tool", "msg");

        tracker.record(a);
        tracker.record(b);

        assertThat(tracker.activeSignatures()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void clearRemovesAllData() {
        FailurePatternTracker tracker = new FailurePatternTracker();
        FailureSignature sig = new FailureSignature("Error", "tool", "msg");
        tracker.record(sig);
        tracker.clear();

        assertThat(tracker.countWithinWindow(sig)).isZero();
        assertThat(tracker.activeSignatures()).isEmpty();
    }

    @Test
    void thresholdLessThanOneThrows() {
        assertThatThrownBy(() -> new FailurePatternTracker(0, Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromExceptionCreatesSignature() {
        RuntimeException ex = new RuntimeException("Something went wrong badly here");
        FailureSignature sig = FailureSignature.fromException(ex, "bash");

        assertThat(sig.errorType()).isEqualTo("RuntimeException");
        assertThat(sig.toolName()).isEqualTo("bash");
        assertThat(sig.messagePrefix()).isEqualTo("Something went wrong badly here");
    }

    @Test
    void longMessageIsTruncated() {
        String longMsg = "x".repeat(200);
        FailureSignature sig = new FailureSignature("Error", "tool", longMsg);
        assertThat(sig.messagePrefix()).hasSize(100);
    }

    @Test
    void nullFieldsDefaultToUnknown() {
        FailureSignature sig = new FailureSignature(null, null, null);
        assertThat(sig.errorType()).isEqualTo("unknown");
        assertThat(sig.toolName()).isEqualTo("unknown");
        assertThat(sig.messagePrefix()).isEmpty();
    }

    @Test
    void patternToSummary() {
        FailurePatternTracker tracker = new FailurePatternTracker(2, Duration.ofDays(30));
        FailureSignature sig = new FailureSignature("IOException", "bash", "Permission denied");
        tracker.record(sig);
        Optional<FailurePattern> pattern = tracker.record(sig);

        assertThat(pattern).isPresent();
        String summary = pattern.get().toSummary();
        assertThat(summary).contains("IOException");
        assertThat(summary).contains("bash");
        assertThat(summary).contains("Permission denied");
    }

    @Test
    void defaultConstructorUsesDefaults() {
        FailurePatternTracker tracker = new FailurePatternTracker();
        assertThat(tracker.threshold()).isEqualTo(3);
        assertThat(tracker.window()).isEqualTo(Duration.ofDays(30));
    }
}
