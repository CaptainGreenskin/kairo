/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AcpSessionManagerTest {

    private final AcpSessionManager manager = new AcpSessionManager();

    @Test
    void newSessionAllocatesUniqueId() {
        var s1 = manager.newSession("/project-a");
        var s2 = manager.newSession("/project-b");
        assertThat(s1.sessionId()).isNotEqualTo(s2.sessionId());
        assertThat(s1.sessionId()).startsWith("acp-");
    }

    @Test
    void getReturnsExistingSession() {
        var session = manager.newSession("/work");
        assertThat(manager.get(session.sessionId())).isPresent();
        assertThat(manager.get(session.sessionId()).get().cwd()).isEqualTo("/work");
    }

    @Test
    void getReturnsEmptyForUnknownId() {
        assertThat(manager.get("nonexistent")).isEmpty();
    }

    @Test
    void putRegistersExternalSession() {
        var state = new AcpSessionManager.AcpSessionState("custom-id", "/custom");
        manager.put(state);
        assertThat(manager.get("custom-id")).isPresent();
        assertThat(manager.get("custom-id").get().cwd()).isEqualTo("/custom");
    }

    @Test
    void forgetRemovesSession() {
        var session = manager.newSession("/tmp");
        manager.forget(session.sessionId());
        assertThat(manager.get(session.sessionId())).isEmpty();
    }

    @Test
    void forgetNonexistentIsNoOp() {
        manager.forget("does-not-exist");
        assertThat(manager.sessionCount()).isEqualTo(0);
    }

    @Test
    void sessionCountReflectsState() {
        assertThat(manager.sessionCount()).isEqualTo(0);
        manager.newSession("/a");
        manager.newSession("/b");
        assertThat(manager.sessionCount()).isEqualTo(2);
    }

    @Test
    void snapshotReturnsImmutableCopy() {
        manager.newSession("/x");
        var snapshot = manager.snapshot();
        manager.newSession("/y");
        assertThat(snapshot).hasSize(1);
        assertThat(manager.sessionCount()).isEqualTo(2);
    }

    @Test
    void cwdIsPreservedCorrectly() {
        var session = manager.newSession("/home/user/project");
        assertThat(session.cwd()).isEqualTo("/home/user/project");
    }

    @Test
    void concurrentNewSessionsDoNotConflict() throws InterruptedException {
        int count = 100;
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            int idx = i;
            threads[i] = new Thread(() -> manager.newSession("/project-" + idx));
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertThat(manager.sessionCount()).isEqualTo(count);
    }
}
