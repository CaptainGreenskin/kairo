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
package io.kairo.core.health;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.AgentState;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentHealthRegistryTest {

    private final AgentHealthRegistry registry = AgentHealthRegistry.global();

    @AfterEach
    void tearDown() {
        registry.deregisterAll();
    }

    private AgentHealthInfo info(String id, AgentState state) {
        return new AgentHealthInfo(id, "agent-" + id, state, 0, Instant.now());
    }

    @Test
    void emptyRegistryReturnsEmptyList() {
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    void registerAndSnapshotReturnsAgent() {
        registry.register("a1", () -> info("a1", AgentState.RUNNING));
        List<AgentHealthInfo> snap = registry.snapshot();
        assertEquals(1, snap.size());
        assertEquals("a1", snap.get(0).agentId());
    }

    @Test
    void snapshotEvictsCompletedAgent() {
        registry.register("done", () -> info("done", AgentState.COMPLETED));
        List<AgentHealthInfo> snap = registry.snapshot();
        assertTrue(snap.isEmpty(), "COMPLETED agent should be evicted");
        // second snapshot should also return empty (entry removed)
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    void snapshotEvictsFailedAgent() {
        registry.register("failed", () -> info("failed", AgentState.FAILED));
        assertTrue(registry.snapshot().isEmpty(), "FAILED agent should be evicted");
    }

    @Test
    void snapshotEvictsThrowingSupplier() {
        registry.register(
                "broken",
                () -> {
                    throw new RuntimeException("oops");
                });
        assertTrue(registry.snapshot().isEmpty(), "Throwing supplier should be evicted");
    }

    @Test
    void deregisterRemovesAgent() {
        registry.register("b1", () -> info("b1", AgentState.RUNNING));
        registry.deregister("b1");
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    void deregisterAllClearsAll() {
        registry.register("c1", () -> info("c1", AgentState.RUNNING));
        registry.register("c2", () -> info("c2", AgentState.IDLE));
        registry.deregisterAll();
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    void idleAndRunningAndSuspendedNotEvicted() {
        registry.register("idle", () -> info("idle", AgentState.IDLE));
        registry.register("running", () -> info("running", AgentState.RUNNING));
        registry.register("suspended", () -> info("suspended", AgentState.SUSPENDED));
        assertEquals(3, registry.snapshot().size());
    }

    @Test
    void agentHealthInfoFieldsCorrect() {
        Instant now = Instant.now();
        AgentHealthInfo info = new AgentHealthInfo("id1", "myAgent", AgentState.RUNNING, 5, now);
        assertEquals("id1", info.agentId());
        assertEquals("myAgent", info.name());
        assertEquals(AgentState.RUNNING, info.state());
        assertEquals(5, info.iterationCount());
        assertEquals(now, info.lastActivityAt());
    }

    @Test
    void concurrentRegistrationAllVisible() throws InterruptedException {
        int count = 20;
        ExecutorService exec = Executors.newFixedThreadPool(count);
        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            String id = "concurrent-" + i;
            exec.submit(
                    () -> {
                        registry.register(id, () -> info(id, AgentState.RUNNING));
                        latch.countDown();
                    });
        }
        latch.await();
        exec.shutdown();
        assertEquals(count, registry.snapshot().size());
    }

    @Test
    void nullInfoFromSupplierIsEvicted() {
        registry.register("null-supplier", () -> null);
        assertTrue(registry.snapshot().isEmpty(), "null info should be evicted");
    }

    @Test
    void multipleSnapshotsAfterEvictionRemainEmpty() {
        registry.register("term", () -> info("term", AgentState.COMPLETED));
        registry.snapshot(); // evicts
        registry.snapshot(); // should still be empty
        assertTrue(registry.snapshot().isEmpty());
    }
}
