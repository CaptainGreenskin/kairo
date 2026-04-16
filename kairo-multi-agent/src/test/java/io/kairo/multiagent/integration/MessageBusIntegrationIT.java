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
package io.kairo.multiagent.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.multiagent.team.InProcessMessageBus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Integration tests for {@link InProcessMessageBus} covering broadcast, point-to-point messaging,
 * concurrent delivery, message ordering, and agent lifecycle operations.
 */
@Tag("integration")
class MessageBusIntegrationIT {

    private InProcessMessageBus bus;

    @BeforeEach
    void setUp() {
        bus = new InProcessMessageBus();
    }

    // ==================== POINT-TO-POINT MESSAGING ====================

    @Test
    void pointToPoint_sendAndPollMultipleMessages() {
        // Send 5 messages from A to B
        for (int i = 0; i < 5; i++) {
            bus.send("agent-A", "agent-B", Msg.of(MsgRole.USER, "msg-" + i))
                    .block();
        }

        List<Msg> polled = bus.poll("agent-B");
        assertEquals(5, polled.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("msg-" + i, polled.get(i).text());
        }

        // Inbox should be drained
        assertTrue(bus.poll("agent-B").isEmpty());
    }

    @Test
    void pointToPoint_multipleSourcesConvergeOnOneRecipient() {
        bus.send("A", "target", Msg.of(MsgRole.USER, "from-A")).block();
        bus.send("B", "target", Msg.of(MsgRole.USER, "from-B")).block();
        bus.send("C", "target", Msg.of(MsgRole.USER, "from-C")).block();

        List<Msg> messages = bus.poll("target");
        assertEquals(3, messages.size());

        // Verify all senders' messages arrived
        List<String> texts = messages.stream().map(Msg::text).toList();
        assertTrue(texts.contains("from-A"));
        assertTrue(texts.contains("from-B"));
        assertTrue(texts.contains("from-C"));
    }

    @Test
    void pointToPoint_isolationBetweenRecipients() {
        bus.send("sender", "R1", Msg.of(MsgRole.USER, "for-R1")).block();
        bus.send("sender", "R2", Msg.of(MsgRole.USER, "for-R2")).block();

        List<Msg> r1 = bus.poll("R1");
        List<Msg> r2 = bus.poll("R2");

        assertEquals(1, r1.size());
        assertEquals("for-R1", r1.get(0).text());
        assertEquals(1, r2.size());
        assertEquals("for-R2", r2.get(0).text());
    }

    // ==================== BROADCAST MESSAGING ====================

    @Test
    void broadcast_deliverToAllRegisteredExceptSender() {
        bus.registerAgent("coordinator");
        bus.registerAgent("worker-1");
        bus.registerAgent("worker-2");
        bus.registerAgent("worker-3");

        bus.broadcast("coordinator", Msg.of(MsgRole.USER, "team update")).block();

        // Coordinator should NOT receive its own broadcast
        assertTrue(bus.poll("coordinator").isEmpty());

        // All workers should receive it
        assertEquals(1, bus.poll("worker-1").size());
        assertEquals(1, bus.poll("worker-2").size());
        assertEquals(1, bus.poll("worker-3").size());
    }

    @Test
    void broadcast_multipleRoundsAccumulate() {
        bus.registerAgent("A");
        bus.registerAgent("B");

        bus.broadcast("A", Msg.of(MsgRole.USER, "round-1")).block();
        bus.broadcast("A", Msg.of(MsgRole.USER, "round-2")).block();

        List<Msg> bMessages = bus.poll("B");
        assertEquals(2, bMessages.size());
        assertEquals("round-1", bMessages.get(0).text());
        assertEquals("round-2", bMessages.get(1).text());
    }

    // ==================== REACTIVE RECEIVE ====================

    @Test
    void reactiveReceive_streamDeliversMessagesInOrder() {
        var flux = bus.receive("listener").take(3);

        bus.send("sender", "listener", Msg.of(MsgRole.USER, "first")).block();
        bus.send("sender", "listener", Msg.of(MsgRole.USER, "second")).block();
        bus.send("sender", "listener", Msg.of(MsgRole.USER, "third")).block();

        StepVerifier.create(flux)
                .expectNextMatches(m -> m.text().equals("first"))
                .expectNextMatches(m -> m.text().equals("second"))
                .expectNextMatches(m -> m.text().equals("third"))
                .verifyComplete();
    }

    @Test
    void reactiveReceive_unregisterCompletesStream() {
        bus.registerAgent("temp-agent");
        var flux = bus.receive("temp-agent");

        bus.unregisterAgent("temp-agent");

        StepVerifier.create(flux).verifyComplete();
    }

    // ==================== CONCURRENT DELIVERY ====================

    @Test
    void concurrentSend_allMessagesDelivered() throws Exception {
        int senderCount = 10;
        int messagesPerSender = 50;
        bus.registerAgent("target");

        ExecutorService executor = Executors.newFixedThreadPool(senderCount);
        CountDownLatch latch = new CountDownLatch(senderCount);

        for (int s = 0; s < senderCount; s++) {
            final int senderIdx = s;
            executor.submit(
                    () -> {
                        try {
                            for (int i = 0; i < messagesPerSender; i++) {
                                bus.send(
                                                "sender-" + senderIdx,
                                                "target",
                                                Msg.of(MsgRole.USER, "s" + senderIdx + "-m" + i))
                                        .block();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        List<Msg> all = bus.poll("target");
        assertEquals(senderCount * messagesPerSender, all.size());
    }

    @Test
    void concurrentBroadcast_allRecipientsGetAllMessages() throws Exception {
        int agentCount = 5;
        for (int i = 0; i < agentCount; i++) {
            bus.registerAgent("agent-" + i);
        }

        int broadcastCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(agentCount);
        CountDownLatch latch = new CountDownLatch(agentCount);

        // Each agent broadcasts once, then does more broadcasts
        for (int a = 0; a < agentCount; a++) {
            final int agentIdx = a;
            executor.submit(
                    () -> {
                        try {
                            for (int b = 0; b < broadcastCount / agentCount; b++) {
                                bus.broadcast(
                                                "agent-" + agentIdx,
                                                Msg.of(MsgRole.USER, "bc-" + agentIdx + "-" + b))
                                        .block();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Each agent should have received broadcasts from all OTHER agents
        for (int i = 0; i < agentCount; i++) {
            List<Msg> messages = bus.poll("agent-" + i);
            // Each agent sends broadcastCount/agentCount messages to (agentCount-1) recipients
            // So each agent receives (agentCount-1) * (broadcastCount/agentCount) messages
            int expectedPerAgent = (agentCount - 1) * (broadcastCount / agentCount);
            assertEquals(
                    expectedPerAgent,
                    messages.size(),
                    "agent-" + i + " should have received " + expectedPerAgent + " messages");
        }
    }

    // ==================== MESSAGE ORDERING ====================

    @Test
    void messageOrdering_singleSenderFIFOGuaranteed() {
        int messageCount = 100;
        for (int i = 0; i < messageCount; i++) {
            bus.send("sender", "receiver", Msg.of(MsgRole.USER, String.valueOf(i))).block();
        }

        List<Msg> polled = bus.poll("receiver");
        assertEquals(messageCount, polled.size());

        // Verify strict FIFO ordering
        for (int i = 0; i < messageCount; i++) {
            assertEquals(
                    String.valueOf(i),
                    polled.get(i).text(),
                    "Message at index " + i + " should be in order");
        }
    }

    // ==================== AGENT LIFECYCLE ====================

    @Test
    void agentLifecycle_registerSendUnregisterCleanup() {
        bus.registerAgent("ephemeral");

        // Send messages
        bus.send("other", "ephemeral", Msg.of(MsgRole.USER, "hello")).block();
        assertEquals(1, bus.poll("ephemeral").size());

        // Unregister
        bus.unregisterAgent("ephemeral");

        // After unregister, inbox is gone
        assertTrue(bus.poll("ephemeral").isEmpty());
    }
}
