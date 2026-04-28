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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AgentProgressTrackerTest {

    @Test
    void initialSnapshotReturnedBeforeAnyUpdate() {
        AgentProgressTracker tracker = new AgentProgressTracker(10);

        ProgressSnapshot snap = tracker.getSnapshot();

        assertThat(snap.currentIteration()).isZero();
        assertThat(snap.maxIterations()).isEqualTo(10);
        assertThat(snap.percentage()).isZero();
        assertThat(snap.currentActivity()).isEqualTo("Initializing");
    }

    @Test
    void updateChangesSnapshot() {
        AgentProgressTracker tracker = new AgentProgressTracker(20);

        tracker.update(5, "running tool", 3, 1024L);
        ProgressSnapshot snap = tracker.getSnapshot();

        assertThat(snap.currentIteration()).isEqualTo(5);
        assertThat(snap.currentActivity()).isEqualTo("running tool");
        assertThat(snap.toolCallsCount()).isEqualTo(3);
        assertThat(snap.tokensUsed()).isEqualTo(1024L);
    }

    @Test
    void updatePreservesMaxIterations() {
        AgentProgressTracker tracker = new AgentProgressTracker(30);

        tracker.update(10, "some activity", 5, 500L);

        assertThat(tracker.getSnapshot().maxIterations()).isEqualTo(30);
    }

    @Test
    void elapsedMsGrowsOverTime() throws InterruptedException {
        AgentProgressTracker tracker = new AgentProgressTracker(10);

        Thread.sleep(20);
        tracker.update(1, "first", 0, 0L);

        assertThat(tracker.getSnapshot().elapsedMs()).isGreaterThanOrEqualTo(10L);
    }

    @Test
    void successiveUpdatesOverwritePrevious() {
        AgentProgressTracker tracker = new AgentProgressTracker(5);

        tracker.update(1, "first", 1, 100L);
        tracker.update(2, "second", 2, 200L);
        ProgressSnapshot snap = tracker.getSnapshot();

        assertThat(snap.currentIteration()).isEqualTo(2);
        assertThat(snap.currentActivity()).isEqualTo("second");
    }

    @Test
    void resetRestoresInitialState() {
        AgentProgressTracker tracker = new AgentProgressTracker(10);
        tracker.update(5, "halfway", 5, 500L);

        tracker.reset(20);
        ProgressSnapshot snap = tracker.getSnapshot();

        assertThat(snap.currentIteration()).isZero();
        assertThat(snap.maxIterations()).isEqualTo(20);
        assertThat(snap.currentActivity()).isEqualTo("Initializing");
    }

    @Test
    void concurrentUpdatesAreThreadSafe() throws InterruptedException {
        AgentProgressTracker tracker = new AgentProgressTracker(100);
        int threadCount = 8;
        int updatesPerThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> errors = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int i = 0; i < updatesPerThread; i++) {
                                tracker.update(
                                        threadId * updatesPerThread + i,
                                        "thread-" + threadId,
                                        i,
                                        i * 10L);
                                // Also read concurrently to verify no corruption
                                ProgressSnapshot snap = tracker.getSnapshot();
                                assertThat(snap).isNotNull();
                                assertThat(snap.currentActivity()).isNotNull();
                            }
                        } catch (Throwable e) {
                            errors.add(e);
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(errors).isEmpty();
    }

    @Test
    void getSnapshotNeverReturnsNull() {
        AgentProgressTracker tracker = new AgentProgressTracker(0);

        assertThat(tracker.getSnapshot()).isNotNull();
    }

    @Test
    void zeroMaxIterationsYieldsZeroPercentage() {
        AgentProgressTracker tracker = new AgentProgressTracker(0);
        tracker.update(5, "running", 2, 100L);

        assertThat(tracker.getSnapshot().percentage()).isZero();
    }
}
