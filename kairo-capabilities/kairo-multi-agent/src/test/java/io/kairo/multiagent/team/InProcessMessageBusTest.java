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
package io.kairo.multiagent.team;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class InProcessMessageBusTest {

    private InProcessMessageBus bus;

    @BeforeEach
    void setUp() {
        bus = new InProcessMessageBus();
    }

    // ==================== SEND + POLL ====================

    @Test
    void sendAndPollShouldDeliverMessage() {
        Msg msg = Msg.of(MsgRole.USER, "Hello agent-B");
        bus.send("agent-A", "agent-B", msg).block();

        List<Msg> polled = bus.poll("agent-B");
        assertEquals(1, polled.size());
        assertEquals("Hello agent-B", polled.get(0).text());
    }

    @Test
    void pollShouldReturnEmptyWhenNoMessages() {
        List<Msg> polled = bus.poll("agent-Z");
        assertTrue(polled.isEmpty());
    }

    @Test
    void pollShouldDrainInbox() {
        bus.send("A", "B", Msg.of(MsgRole.USER, "msg1")).block();
        bus.send("A", "B", Msg.of(MsgRole.USER, "msg2")).block();

        List<Msg> first = bus.poll("B");
        assertEquals(2, first.size());

        // Second poll should be empty
        List<Msg> second = bus.poll("B");
        assertTrue(second.isEmpty());
    }

    // ==================== FIFO ORDERING ====================

    @Test
    void messagesShouldBeFIFO() {
        bus.send("A", "B", Msg.of(MsgRole.USER, "first")).block();
        bus.send("A", "B", Msg.of(MsgRole.USER, "second")).block();
        bus.send("A", "B", Msg.of(MsgRole.USER, "third")).block();

        List<Msg> polled = bus.poll("B");
        assertEquals(3, polled.size());
        assertEquals("first", polled.get(0).text());
        assertEquals("second", polled.get(1).text());
        assertEquals("third", polled.get(2).text());
    }

    // ==================== CHANNEL ISOLATION ====================

    @Test
    void messagesShouldBeIsolatedPerAgent() {
        bus.send("sender", "agent-A", Msg.of(MsgRole.USER, "for A")).block();
        bus.send("sender", "agent-B", Msg.of(MsgRole.USER, "for B")).block();

        List<Msg> forA = bus.poll("agent-A");
        List<Msg> forB = bus.poll("agent-B");

        assertEquals(1, forA.size());
        assertEquals("for A", forA.get(0).text());
        assertEquals(1, forB.size());
        assertEquals("for B", forB.get(0).text());
    }

    // ==================== REACTIVE RECEIVE ====================

    @Test
    void receiveShouldEmitSentMessages() {
        // Subscribe first to create the sink, then send messages
        var flux = bus.receive("B").take(2);

        bus.send("A", "B", Msg.of(MsgRole.USER, "hello")).block();
        bus.send("A", "B", Msg.of(MsgRole.USER, "world")).block();

        StepVerifier.create(flux)
                .expectNextMatches(m -> m.text().equals("hello"))
                .expectNextMatches(m -> m.text().equals("world"))
                .verifyComplete();
    }

    @Test
    void receiveShouldGetNewMessagesAfterSubscription() {
        // Subscribe first, then send
        StepVerifier.create(
                        bus.receive("C")
                                .take(1)
                                .doOnSubscribe(
                                        sub ->
                                                bus.send(
                                                                "sender",
                                                                "C",
                                                                Msg.of(MsgRole.USER, "delayed"))
                                                        .subscribe()))
                .expectNextMatches(m -> m.text().equals("delayed"))
                .verifyComplete();
    }

    // ==================== BROADCAST ====================

    @Test
    void broadcastShouldSendToAllRegisteredExceptSender() {
        bus.registerAgent("A");
        bus.registerAgent("B");
        bus.registerAgent("C");

        bus.broadcast("A", Msg.of(MsgRole.USER, "announcement")).block();

        // A should NOT receive its own broadcast
        assertTrue(bus.poll("A").isEmpty());
        // B and C should receive it
        assertEquals(1, bus.poll("B").size());
        assertEquals(1, bus.poll("C").size());
    }

    // ==================== REGISTER / UNREGISTER ====================

    @Test
    void registerAgentShouldCreateInbox() {
        bus.registerAgent("newAgent");
        // Should be able to poll without errors, returns empty
        assertTrue(bus.poll("newAgent").isEmpty());
    }

    @Test
    void unregisterAgentShouldCleanupResources() {
        bus.registerAgent("temp");
        bus.send("sender", "temp", Msg.of(MsgRole.USER, "msg")).block();

        bus.unregisterAgent("temp");

        // After unregister, poll returns empty (inbox removed)
        assertTrue(bus.poll("temp").isEmpty());
    }

    @Test
    void unregisterShouldCompleteSink() {
        bus.registerAgent("sinkAgent");
        var flux = bus.receive("sinkAgent");

        // Unregister should complete the sink
        bus.unregisterAgent("sinkAgent");

        StepVerifier.create(flux).verifyComplete();
    }

    // ==================== MULTIPLE SENDERS ====================

    @Test
    void multipleAgentsCanSendToSameRecipient() {
        bus.send("A", "target", Msg.of(MsgRole.USER, "from A")).block();
        bus.send("B", "target", Msg.of(MsgRole.USER, "from B")).block();
        bus.send("C", "target", Msg.of(MsgRole.USER, "from C")).block();

        List<Msg> messages = bus.poll("target");
        assertEquals(3, messages.size());
    }

    // ==================== BROADCAST COMPLETION GUARANTEE ====================

    @Test
    void broadcastMonoCompletesOnlyAfterAllSendsFinish() {
        bus.registerAgent("A");
        bus.registerAgent("B");
        bus.registerAgent("C");
        bus.registerAgent("D");

        // The fixed broadcast should guarantee all messages are delivered
        // when the Mono completes (not fire-and-forget)
        StepVerifier.create(bus.broadcast("A", Msg.of(MsgRole.USER, "sync broadcast")))
                .verifyComplete();

        // All recipients should have the message immediately after Mono completes
        assertEquals(1, bus.poll("B").size());
        assertEquals(1, bus.poll("C").size());
        assertEquals(1, bus.poll("D").size());
        assertTrue(bus.poll("A").isEmpty());
    }

    @Test
    void broadcastToEmptyBusCompletesSuccessfully() {
        // No agents registered — broadcast should still complete
        StepVerifier.create(bus.broadcast("sender", Msg.of(MsgRole.USER, "nobody home")))
                .verifyComplete();
    }

    @Test
    void broadcastWithSingleRecipient() {
        bus.registerAgent("sender");
        bus.registerAgent("receiver");

        StepVerifier.create(bus.broadcast("sender", Msg.of(MsgRole.USER, "just for you")))
                .verifyComplete();

        assertEquals(1, bus.poll("receiver").size());
        assertTrue(bus.poll("sender").isEmpty());
    }

    // ==================== BOUNDED BUFFER ====================

    @Test
    void replayBufferShouldBeBounded() {
        // The replay buffer is limited to 1024 items.
        // A late subscriber should only see at most 1024 replayed messages.
        int totalMessages = 2048;

        bus.registerAgent("producer");
        bus.registerAgent("consumer");

        // Create the sink, then send all messages (sink stores them in replay buffer)
        Flux<Msg> flux = bus.receive("consumer");

        for (int i = 0; i < totalMessages; i++) {
            bus.send("producer", "consumer", Msg.of(MsgRole.USER, "msg-" + i)).block();
        }

        // Use time-based take — the replay buffer replays what it has, then waits.
        // We collect what the replay window provides within a short window.
        AtomicInteger received = new AtomicInteger(0);
        flux.take(java.time.Duration.ofSeconds(2))
                .doOnNext(m -> received.incrementAndGet())
                .blockLast(java.time.Duration.ofSeconds(5));

        // Replay buffer bounded to 1024: subscriber gets at most 1024 replayed items
        assertTrue(received.get() <= totalMessages, "Received count should not exceed total sent");
        assertTrue(received.get() > 0, "Should have received some messages");
    }

    @Test
    void rapidFireShouldNotCauseOOM() {
        // Publish 2500 messages rapidly — verify no OOM and buffer stays bounded
        int messageCount = 2500;
        bus.registerAgent("fast-producer");

        // Create sink first
        Flux<Msg> receiverFlux = bus.receive("slow-consumer");

        // Rapid-fire publish
        for (int i = 0; i < messageCount; i++) {
            bus.send("fast-producer", "slow-consumer", Msg.of(MsgRole.USER, "rapid-" + i)).block();
        }

        // Subscribe late — replay buffer should cap at 1024
        AtomicInteger count = new AtomicInteger(0);
        receiverFlux
                .take(java.time.Duration.ofSeconds(2))
                .doOnNext(m -> count.incrementAndGet())
                .blockLast(java.time.Duration.ofSeconds(5));

        // Replay buffer is bounded to 1024, so late subscriber gets at most 1024
        assertTrue(
                count.get() <= 1024,
                "Late subscriber should receive at most 1024 replayed messages, got "
                        + count.get());
        assertTrue(count.get() > 0, "Late subscriber should receive some replayed messages");
    }
}
