/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.lsp.LspException;
import io.kairo.api.lsp.ServerDef;
import io.kairo.api.lsp.WorkspaceRoot;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LspClientPoolTest {

    private static final ServerDef FAKE =
            new ServerDef("fake", "Fake", Set.of("py"), Set.of(), List.of("fake"), "python");

    @Test
    void acquireReusesClientForSameKey(@TempDir Path root) {
        AtomicInteger created = new AtomicInteger();
        LspClientPool pool =
                new LspClientPool(
                        (def, r) -> {
                            created.incrementAndGet();
                            return new AlwaysHealthyClient(def, r);
                        },
                        Duration.ofMinutes(5));
        var first = pool.acquire(FAKE, root);
        var second = pool.acquire(FAKE, root);
        assertThat(created.get()).isEqualTo(1);
        assertThat(first).isSameAs(second);
        pool.close();
    }

    @Test
    void brokenSpawnGoesIntoBrokenSetAndReturnsNull(@TempDir Path root) {
        LspClientPool pool =
                new LspClientPool(
                        (def, r) -> {
                            return new AlwaysFailingClient(def, r);
                        },
                        Duration.ofMinutes(5));
        assertThat(pool.acquire(FAKE, root)).isNull();
        assertThat(pool.brokenKeys()).contains(new WorkspaceRoot("fake", root));
        // Subsequent acquires keep returning null without re-spawning.
        assertThat(pool.acquire(FAKE, root)).isNull();
        pool.close();
    }

    @Test
    void forgetClearsBrokenSet(@TempDir Path root) {
        LspClientPool pool =
                new LspClientPool(
                        (def, r) -> new AlwaysFailingClient(def, r), Duration.ofMinutes(5));
        pool.acquire(FAKE, root);
        WorkspaceRoot key = new WorkspaceRoot("fake", root);
        assertThat(pool.brokenKeys()).contains(key);
        pool.forget(key);
        assertThat(pool.brokenKeys()).doesNotContain(key);
        pool.close();
    }

    @Test
    void differentRootsGetIndependentClients(@TempDir Path a, @TempDir Path b) {
        AtomicInteger created = new AtomicInteger();
        LspClientPool pool =
                new LspClientPool(
                        (def, r) -> {
                            created.incrementAndGet();
                            return new AlwaysHealthyClient(def, r);
                        },
                        Duration.ofMinutes(5));
        pool.acquire(FAKE, a);
        pool.acquire(FAKE, b);
        assertThat(created.get()).isEqualTo(2);
        pool.close();
    }

    private static class AlwaysHealthyClient extends LspClient {
        AlwaysHealthyClient(ServerDef def, Path root) {
            super(def, root);
        }

        @Override
        public synchronized void start() {
            // pretend we started and stay healthy
            setRunningForTest();
        }

        private void setRunningForTest() {
            try {
                var f = LspClient.class.getDeclaredField("running");
                f.setAccessible(true);
                ((java.util.concurrent.atomic.AtomicBoolean) f.get(this)).set(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void shutdown() {
            // no-op
        }
    }

    private static class AlwaysFailingClient extends LspClient {
        AlwaysFailingClient(ServerDef def, Path root) {
            super(def, root);
        }

        @Override
        public synchronized void start() {
            throw new LspException("spawn failed");
        }

        @Override
        public synchronized void shutdown() {
            // no-op
        }
    }
}
