/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.acp.AcpSessionUpdate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class StreamingAcpBridgeTest {

    @Test
    void subscribeSendsUpdatesToSink() throws Exception {
        List<AcpSessionUpdate> received = new ArrayList<>();
        StreamingAcpBridge bridge =
                (sessionId, sink) -> {
                    sink.accept(new AcpSessionUpdate.AgentMessageChunk(sessionId, "Hello "));
                    sink.accept(new AcpSessionUpdate.AgentMessageChunk(sessionId, "World"));
                    return () -> {};
                };

        AutoCloseable unsub = bridge.subscribe("test-session", received::add);
        assertThat(received).hasSize(2);
        unsub.close();
    }

    @Test
    void unsubscribeCallbackIsIdempotent() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        StreamingAcpBridge bridge =
                (sessionId, sink) -> {
                    return () -> closed.set(true);
                };

        AutoCloseable unsub = bridge.subscribe("s1", update -> {});
        unsub.close();
        assertThat(closed.get()).isTrue();
        unsub.close();
    }

    @Test
    void bridgeWithoutStreamingIsNoOp() {
        StreamingAcpBridge bridge = (sessionId, sink) -> () -> {};
        AutoCloseable unsub = bridge.subscribe("s1", update -> {});
        assertThat(unsub).isNotNull();
    }

    @Test
    void multipleSessionsAreIndependent() throws Exception {
        List<String> session1Updates = new ArrayList<>();
        List<String> session2Updates = new ArrayList<>();

        StreamingAcpBridge bridge =
                (sessionId, sink) -> {
                    sink.accept(
                            new AcpSessionUpdate.AgentMessageChunk(
                                    sessionId, "msg-for-" + sessionId));
                    return () -> {};
                };

        bridge.subscribe(
                "s1",
                u -> {
                    if (u instanceof AcpSessionUpdate.AgentMessageChunk c)
                        session1Updates.add(c.text());
                });
        bridge.subscribe(
                "s2",
                u -> {
                    if (u instanceof AcpSessionUpdate.AgentMessageChunk c)
                        session2Updates.add(c.text());
                });

        assertThat(session1Updates).containsExactly("msg-for-s1");
        assertThat(session2Updates).containsExactly("msg-for-s2");
    }
}
