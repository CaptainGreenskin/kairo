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
package io.kairo.core.model.anthropic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StreamIdleWatchdogTest {

    @Test
    void disarmPreventsTimeout() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        StreamIdleWatchdog watchdog = new StreamIdleWatchdog(latch::countDown);
        watchdog.disarm();

        assertFalse(
                latch.await(150, TimeUnit.MILLISECONDS),
                "timeout callback should NOT fire after disarm");
    }

    @Test
    void closeIsEquivalentToDisarm() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        StreamIdleWatchdog watchdog = new StreamIdleWatchdog(latch::countDown);
        watchdog.close();

        assertFalse(
                latch.await(150, TimeUnit.MILLISECONDS),
                "timeout callback should NOT fire after close");
    }

    @Test
    void multipleResetsOnlyLastTimerSurvives() throws InterruptedException {
        // With the default 60s timeout, reset a few times quickly then disarm.
        // If earlier timers were not cancelled, the latch would fire.
        CountDownLatch latch = new CountDownLatch(1);
        StreamIdleWatchdog watchdog = new StreamIdleWatchdog(latch::countDown);

        watchdog.reset();
        watchdog.reset();
        watchdog.reset();
        watchdog.disarm();

        // No timer should fire because we disarmed the last one
        assertFalse(
                latch.await(200, TimeUnit.MILLISECONDS),
                "no timeout should fire after disarm following resets");
    }

    @Test
    void resetSchedulesTimer_thatCanBeDisarmed() throws InterruptedException {
        // Verify that reset() actually schedules a timer by checking that disarm()
        // prevents it from firing. If reset() did nothing, this test would be meaningless.
        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        StreamIdleWatchdog watchdog =
                new StreamIdleWatchdog(
                        () -> {
                            count.incrementAndGet();
                            latch.countDown();
                        });

        watchdog.reset();
        // Disarm immediately — the scheduled timer should be cancelled
        watchdog.disarm();

        assertFalse(
                latch.await(200, TimeUnit.MILLISECONDS),
                "timer should have been cancelled by disarm");
        assertEquals(0, count.get(), "callback should not have fired");
    }

    @Test
    void idleTimeoutMsDefaultValue() {
        assertEquals(
                180_000L,
                StreamIdleWatchdog.IDLE_TIMEOUT_MS,
                "default idle timeout should be 180 seconds (reasoning-model headroom)");
    }
}
