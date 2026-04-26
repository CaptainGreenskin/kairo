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
package io.kairo.expertteam.tck;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/**
 * Lightweight deterministic {@link Agent} used by the TCK and unit tests.
 *
 * <p>Wraps a caller-supplied {@code Function<Msg, Mono<Msg>>} (or a simpler text responder). Does
 * not attempt to implement the full agent runtime — tool use, model dispatch, and snapshotting are
 * all out of scope for orchestration-level tests.
 *
 * @since v0.10 (Experimental)
 */
public final class StubAgent implements Agent {

    private final String id;
    private final String name;
    private final Function<Msg, Mono<Msg>> handler;
    private final AtomicInteger invocations = new AtomicInteger();

    public StubAgent(String name, Function<Msg, Mono<Msg>> handler) {
        this(UUID.randomUUID().toString(), name, handler);
    }

    public StubAgent(String id, String name, Function<Msg, Mono<Msg>> handler) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
    }

    /** Factory producing an agent that always returns a fixed text response. */
    public static StubAgent fixed(String name, String response) {
        return new StubAgent(name, msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, response)));
    }

    /** Factory for an agent that always fails synchronously. */
    public static StubAgent failing(String name, RuntimeException error) {
        return new StubAgent(name, msg -> Mono.error(error));
    }

    @Override
    public Mono<Msg> call(Msg input) {
        invocations.incrementAndGet();
        return handler.apply(input);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AgentState state() {
        return AgentState.IDLE;
    }

    @Override
    public void interrupt() {
        // no-op
    }

    /** Number of times {@link #call(Msg)} has been invoked. */
    public int invocationCount() {
        return invocations.get();
    }
}
