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
package io.kairo.core.hook;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.hook.HookSessionContext;
import io.kairo.api.hook.NoopHookSessionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class DefaultHookSessionContextTest {

    @Test
    void sessionIdIsPreserved() {
        var ctx = new DefaultHookSessionContext("session-123");
        assertEquals("session-123", ctx.sessionId());
    }

    @Test
    void getSetTypeSafe() {
        var ctx = new DefaultHookSessionContext("s1");
        ctx.set("warned", true);
        assertTrue(ctx.get("warned", Boolean.class));
        assertNull(ctx.get("warned", String.class));
    }

    @Test
    void getMissingKeyReturnsNull() {
        var ctx = new DefaultHookSessionContext("s1");
        assertNull(ctx.get("nonexistent", String.class));
    }

    @Test
    void setNullRemovesKey() {
        var ctx = new DefaultHookSessionContext("s1");
        ctx.set("key", "value");
        assertEquals("value", ctx.get("key", String.class));
        ctx.set("key", null);
        assertNull(ctx.get("key", String.class));
    }

    @Test
    void incrementCounterStartsAtOne() {
        var ctx = new DefaultHookSessionContext("s1");
        assertEquals(1, ctx.incrementCounter("calls"));
        assertEquals(2, ctx.incrementCounter("calls"));
        assertEquals(3, ctx.incrementCounter("calls"));
    }

    @Test
    void getCounterWithoutIncrementReturnsZero() {
        var ctx = new DefaultHookSessionContext("s1");
        assertEquals(0, ctx.getCounter("nonexistent"));
    }

    @Test
    void getCounterReflectsIncrements() {
        var ctx = new DefaultHookSessionContext("s1");
        ctx.incrementCounter("tool_calls");
        ctx.incrementCounter("tool_calls");
        assertEquals(2, ctx.getCounter("tool_calls"));
    }

    @Test
    void concurrentIncrementIsSafe() throws Exception {
        var ctx = new DefaultHookSessionContext("s1");
        int threads = 8;
        int incrementsPerThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(
                    pool.submit(
                            () -> {
                                try {
                                    latch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                for (int i = 0; i < incrementsPerThread; i++) {
                                    ctx.incrementCounter("concurrent");
                                }
                            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(threads * incrementsPerThread, ctx.getCounter("concurrent"));
    }

    @Test
    void noopContextDiscards() {
        HookSessionContext noop = NoopHookSessionContext.INSTANCE;
        assertEquals("", noop.sessionId());
        noop.set("key", "value");
        assertNull(noop.get("key", String.class));
        assertEquals(0, noop.incrementCounter("x"));
        assertEquals(0, noop.getCounter("x"));
    }
}
