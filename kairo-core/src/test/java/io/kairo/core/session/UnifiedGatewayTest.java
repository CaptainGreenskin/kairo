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
import io.kairo.api.message.MsgRole;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for {@link UnifiedGateway} — the per-session call serialiser + drain controller. The
 * concurrency-limit, drain, and per-session-serialise paths are the ones a regression would expose
 * as production hangs or accept-during-shutdown bugs.
 */
class UnifiedGatewayTest {

    private AgentSessionPool pool;
    private UnifiedGateway gateway;

    @AfterEach
    void teardown() {
        if (gateway != null) gateway.shutdown();
        else if (pool != null) pool.shutdown();
    }

    @Test
    void route_callsAgentAndReturnsItsResponse() {
        StubAgent agent = new StubAgent(Mono.just(Msg.of(MsgRole.ASSISTANT, "ok")));
        pool = new AgentSessionPool(4, Duration.ofMinutes(60), k -> agent, k -> {});
        gateway = new UnifiedGateway(pool, 16);

        StepVerifier.create(gateway.route(SessionKey.of("ch", "u"), Msg.of(MsgRole.USER, "hi")))
                .assertNext(msg -> assertThat(msg.text()).isEqualTo("ok"))
                .verifyComplete();
        assertThat(agent.callCount.get()).isEqualTo(1);
    }

    @Test
    void route_whenDraining_returnsIllegalStateError() {
        pool =
                new AgentSessionPool(
                        4, Duration.ofMinutes(60), k -> new StubAgent(Mono.empty()), k -> {});
        gateway = new UnifiedGateway(pool, 16);
        gateway.startDrain();

        StepVerifier.create(gateway.route(SessionKey.of("ch", "u"), Msg.of(MsgRole.USER, "hi")))
                .expectErrorMatches(
                        e ->
                                e instanceof IllegalStateException
                                        && e.getMessage().contains("shutting down"))
                .verify();
    }

    @Test
    void route_whenActiveRequestsAtMax_returnsConcurrencyLimitError() {
        // Force maxConcurrent=0 so the first route() trips the limit immediately — keeps the
        // test deterministic without needing real concurrency.
        pool =
                new AgentSessionPool(
                        4, Duration.ofMinutes(60), k -> new StubAgent(Mono.empty()), k -> {});
        gateway = new UnifiedGateway(pool, 0);

        StepVerifier.create(gateway.route(SessionKey.of("ch", "u"), Msg.of(MsgRole.USER, "hi")))
                .expectError(ConcurrencyLimitExceededException.class)
                .verify();
    }

    @Test
    void route_releasesActiveRequestSlotAfterCompletion() {
        StubAgent agent = new StubAgent(Mono.just(Msg.of(MsgRole.ASSISTANT, "done")));
        pool = new AgentSessionPool(4, Duration.ofMinutes(60), k -> agent, k -> {});
        gateway = new UnifiedGateway(pool, 16);

        // Two sequential calls should both succeed — the active-request slot must be released
        // by the doFinally on success.
        StepVerifier.create(gateway.route(SessionKey.of("ch", "u"), Msg.of(MsgRole.USER, "1")))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(gateway.route(SessionKey.of("ch", "u"), Msg.of(MsgRole.USER, "2")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(gateway.activeRequestCount()).isZero();
    }

    @Test
    void interrupt_callsAgentInterruptOnly_whenPresent() {
        StubAgent agent = new StubAgent(Mono.empty());
        pool = new AgentSessionPool(4, Duration.ofMinutes(60), k -> agent, k -> {});
        gateway = new UnifiedGateway(pool);

        SessionKey key = SessionKey.of("ch", "u");
        // Without prior get/route the agent isn't in the pool — interrupt is a no-op (does not
        // NPE).
        gateway.interrupt(key);
        assertThat(agent.interruptCount.get()).isZero();

        // Now seed and interrupt for real.
        pool.getOrCreate(key);
        gateway.interrupt(key);
        assertThat(agent.interruptCount.get()).isEqualTo(1);
    }

    @Test
    void startDrain_setsDrainingFlag_andSubsequentRoutesAreRejected() {
        pool =
                new AgentSessionPool(
                        4, Duration.ofMinutes(60), k -> new StubAgent(Mono.empty()), k -> {});
        gateway = new UnifiedGateway(pool, 16);
        assertThat(gateway.isDraining()).isFalse();

        gateway.startDrain();

        assertThat(gateway.isDraining()).isTrue();
        StepVerifier.create(gateway.route(SessionKey.of("ch", "u"), Msg.of(MsgRole.USER, "x")))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void awaitDrain_returnsTrueWhenNoActiveRequests() {
        pool =
                new AgentSessionPool(
                        4, Duration.ofMinutes(60), k -> new StubAgent(Mono.empty()), k -> {});
        gateway = new UnifiedGateway(pool, 16);
        // Nothing in flight → returns immediately.
        assertThat(gateway.awaitDrain(1000)).isTrue();
    }

    @Test
    void pool_accessorReturnsUnderlyingPool() {
        pool =
                new AgentSessionPool(
                        4, Duration.ofMinutes(60), k -> new StubAgent(Mono.empty()), k -> {});
        gateway = new UnifiedGateway(pool);
        assertThat(gateway.pool()).isSameAs(pool);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Stub Agent that records interrupt + call invocations and returns a canned Mono. */
    static final class StubAgent implements Agent {
        final AtomicInteger callCount = new AtomicInteger();
        final AtomicInteger interruptCount = new AtomicInteger();
        private final Mono<Msg> response;
        private final String id = UUID.randomUUID().toString();

        StubAgent(Mono<Msg> response) {
            this.response = response;
        }

        @Override
        public Mono<Msg> call(Msg input) {
            callCount.incrementAndGet();
            return response;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return "stub";
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
