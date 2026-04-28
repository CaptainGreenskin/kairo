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
package io.kairo.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProgressSnapshotTest {

    @Test
    void initialSnapshotHasZeroIteration() {
        ProgressSnapshot snap = ProgressSnapshot.initial(50);

        assertThat(snap.currentIteration()).isZero();
        assertThat(snap.maxIterations()).isEqualTo(50);
        assertThat(snap.percentage()).isZero();
        assertThat(snap.currentActivity()).isEqualTo("Initializing");
        assertThat(snap.elapsedMs()).isZero();
        assertThat(snap.toolCallsCount()).isZero();
        assertThat(snap.tokensUsed()).isZero();
    }

    @Test
    void initialSnapshotWithZeroMaxIterations() {
        ProgressSnapshot snap = ProgressSnapshot.initial(0);

        assertThat(snap.maxIterations()).isZero();
        assertThat(snap.percentage()).isZero();
    }

    @Test
    void percentageCalculatedCorrectly() {
        ProgressSnapshot snap = ProgressSnapshot.of(5, 10, "working", 1000L, 3, 500L);

        assertThat(snap.percentage()).isEqualTo(50);
    }

    @Test
    void percentageClampedAt100() {
        ProgressSnapshot snap = ProgressSnapshot.of(15, 10, "done", 5000L, 10, 1000L);

        assertThat(snap.percentage()).isEqualTo(100);
    }

    @Test
    void percentageZeroWhenMaxIterationsZero() {
        ProgressSnapshot snap = ProgressSnapshot.of(5, 0, "running", 2000L, 1, 100L);

        assertThat(snap.percentage()).isZero();
    }

    @Test
    void ofSnapshotPreservesAllFields() {
        ProgressSnapshot snap = ProgressSnapshot.of(3, 20, "tool call: read", 7500L, 5, 2048L);

        assertThat(snap.currentIteration()).isEqualTo(3);
        assertThat(snap.maxIterations()).isEqualTo(20);
        assertThat(snap.currentActivity()).isEqualTo("tool call: read");
        assertThat(snap.elapsedMs()).isEqualTo(7500L);
        assertThat(snap.toolCallsCount()).isEqualTo(5);
        assertThat(snap.tokensUsed()).isEqualTo(2048L);
    }

    @Test
    void percentageRoundsDown() {
        // 3 / 10 = 30%
        ProgressSnapshot snap = ProgressSnapshot.of(3, 10, "x", 0L, 0, 0L);
        assertThat(snap.percentage()).isEqualTo(30);
    }

    @Test
    void percentageAt1of100() {
        ProgressSnapshot snap = ProgressSnapshot.of(1, 100, "x", 0L, 0, 0L);
        assertThat(snap.percentage()).isEqualTo(1);
    }

    @Test
    void percentageAt100of100() {
        ProgressSnapshot snap = ProgressSnapshot.of(100, 100, "done", 0L, 0, 0L);
        assertThat(snap.percentage()).isEqualTo(100);
    }

    @Test
    void snapshotIsImmutableRecord() {
        ProgressSnapshot a = ProgressSnapshot.of(1, 5, "a", 100L, 1, 50L);
        ProgressSnapshot b = ProgressSnapshot.of(1, 5, "a", 100L, 1, 50L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
