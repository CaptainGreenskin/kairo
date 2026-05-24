/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Tests for {@link AgentSessionPool}. The pool is non-trivial — LRU eviction, idle sweep, agent
 * interrupt on evict — so these tests pin the contract corners that production callers rely on.
 * Background sweep is exercised via a custom-constructed pool with a short idle TTL plus a manual
 * sleep; we don't depend on the 5-minute scheduled sweep firing during a unit test run.
 */
class AgentSessionPoolTest {

    private AgentSessionPool pool;

    @AfterEach
    void teardown() {
        if (pool != null) pool.shutdown();
    }

    @Test
    void getOrCreate_returnsNewAgentForUnknownKey() {
        AtomicInteger created = new AtomicInteger();
        pool = makePool(8, Duration.ofMinutes(60), k -> stubAgent(created));
        SessionKey key = SessionKey.of("ch", "u");

        Agent agent = pool.getOrCreate(key);

        assertThat(agent).isNotNull();
        assertThat(created.get()).isEqualTo(1);
        assertThat(pool.size()).isEqualTo(1);
    }

    @Test
    void getOrCreate_returnsExistingAgentForKnownKey() {
        AtomicInteger created = new AtomicInteger();
        pool = makePool(8, Duration.ofMinutes(60), k -> stubAgent(created));
        SessionKey key = SessionKey.of("ch", "u");

        Agent first = pool.getOrCreate(key);
        Agent second = pool.getOrCreate(key);

        assertThat(second).isSameAs(first);
        assertThat(created.get())
                .as("factory must not be re-invoked when key is in pool")
                .isEqualTo(1);
    }

    @Test
    void get_returnsNullForUnknownKey() {
        pool = makePool(8, Duration.ofMinutes(60), k -> stubAgent(new AtomicInteger()));
        assertThat(pool.get(SessionKey.of("ch", "missing"))).isNull();
    }

    @Test
    void evict_removesEntry_interruptsAgent_andFiresCallback() {
        RecordingAgent[] holder = new RecordingAgent[1];
        List<SessionKey> evicted = new ArrayList<>();
        pool =
                new AgentSessionPool(
                        8,
                        Duration.ofMinutes(60),
                        k -> {
                            holder[0] = new RecordingAgent();
                            return holder[0];
                        },
                        evicted::add);
        SessionKey key = SessionKey.of("ch", "u");
        pool.getOrCreate(key);

        pool.evict(key);

        assertThat(pool.get(key)).isNull();
        assertThat(pool.size()).isZero();
        assertThat(holder[0].interruptCount.get()).isEqualTo(1);
        assertThat(evicted).containsExactly(key);
    }

    @Test
    void evictExcess_LRUEvictsEldestWhenOverCapacity() {
        // Pool size = 2 → 3rd insert must evict the first (LRU = "least recently accessed").
        List<RecordingAgent> agents = new ArrayList<>();
        List<SessionKey> evicted = new ArrayList<>();
        pool =
                new AgentSessionPool(
                        2,
                        Duration.ofMinutes(60),
                        k -> {
                            RecordingAgent ra = new RecordingAgent();
                            agents.add(ra);
                            return ra;
                        },
                        evicted::add);
        SessionKey k1 = SessionKey.of("ch", "u1");
        SessionKey k2 = SessionKey.of("ch", "u2");
        SessionKey k3 = SessionKey.of("ch", "u3");

        pool.getOrCreate(k1);
        pool.getOrCreate(k2);
        // Touching k1 promotes it past k2 in the access-order map.
        pool.getOrCreate(k1);
        pool.getOrCreate(k3);

        assertThat(pool.size()).isEqualTo(2);
        // k2 was the LRU at the moment of overflow, so it gets evicted.
        assertThat(evicted).containsExactly(k2);
        assertThat(agents.get(1).interruptCount.get())
                .as("evicted agent must be interrupted")
                .isEqualTo(1);
    }

    @Test
    void replace_swapsAgentAndInterruptsOld() {
        RecordingAgent firstAgent = new RecordingAgent();
        RecordingAgent secondAgent = new RecordingAgent();
        pool = new AgentSessionPool(8, Duration.ofMinutes(60), k -> firstAgent, k -> {});
        SessionKey key = SessionKey.of("ch", "u");
        pool.getOrCreate(key);

        Agent returned = pool.replace(key, secondAgent);

        assertThat(returned).isSameAs(secondAgent);
        assertThat(pool.get(key)).isSameAs(secondAgent);
        assertThat(firstAgent.interruptCount.get())
                .as("old agent must be interrupted when replaced")
                .isEqualTo(1);
        assertThat(secondAgent.interruptCount.get())
                .as("new agent must NOT be interrupted")
                .isZero();
    }

    @Test
    void shutdown_interruptsAllAgentsAndClearsPool() {
        List<RecordingAgent> agents = new ArrayList<>();
        pool =
                new AgentSessionPool(
                        8,
                        Duration.ofMinutes(60),
                        k -> {
                            RecordingAgent ra = new RecordingAgent();
                            agents.add(ra);
                            return ra;
                        },
                        k -> {});
        pool.getOrCreate(SessionKey.of("ch", "u1"));
        pool.getOrCreate(SessionKey.of("ch", "u2"));

        pool.shutdown();

        assertThat(pool.size()).isZero();
        for (RecordingAgent a : agents) {
            assertThat(a.interruptCount.get())
                    .as("shutdown must interrupt every pooled agent")
                    .isEqualTo(1);
        }
        // Mark `pool` null so teardown doesn't double-shutdown.
        pool = null;
    }

    @Test
    void constructor_acceptsGatewayConfigOverload() {
        // The convenience ctor is the one Spring autoconfig will call.
        GatewayConfig cfg = new GatewayConfig(4, Duration.ofMinutes(5), 0.5f);
        pool = new AgentSessionPool(cfg, k -> stubAgent(new AtomicInteger()));
        pool.getOrCreate(SessionKey.of("ch", "u"));
        assertThat(pool.size()).isEqualTo(1);
    }

    @Test
    void nullEvictionCallback_doesNotThrow() {
        // Defensive default — pool replaces null with a no-op.
        pool = new AgentSessionPool(8, Duration.ofMinutes(60), k -> new RecordingAgent(), null);
        SessionKey key = SessionKey.of("ch", "u");
        pool.getOrCreate(key);
        pool.evict(key); // must not NPE
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static AgentSessionPool makePool(
            int max, Duration idleTtl, java.util.function.Function<SessionKey, Agent> factory) {
        return new AgentSessionPool(max, idleTtl, factory, k -> {});
    }

    private static Agent stubAgent(AtomicInteger createdCounter) {
        createdCounter.incrementAndGet();
        return new RecordingAgent();
    }

    /** Stub Agent that records interrupt count — enough for pool eviction assertions. */
    static final class RecordingAgent implements Agent {
        final AtomicInteger interruptCount = new AtomicInteger();
        private final String id = UUID.randomUUID().toString();

        @Override
        public Mono<Msg> call(Msg input) {
            return Mono.empty();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return "test-agent";
        }

        @Override
        public AgentState state() {
            return AgentState.IDLE;
        }

        @Override
        public void interrupt() {
            interruptCount.incrementAndGet();
        }
    }
}
