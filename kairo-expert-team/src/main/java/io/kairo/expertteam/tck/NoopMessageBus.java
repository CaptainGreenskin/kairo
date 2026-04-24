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

import io.kairo.api.message.Msg;
import io.kairo.api.team.MessageBus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * No-op {@link MessageBus} used by tests that do not exercise inter-agent messaging.
 *
 * <p>{@link #receive(String)} returns an empty {@link Flux}; {@link #send(String, String, Msg)} and
 * {@link #broadcast(String, Msg)} both complete immediately. Adequate for expert-team tests that
 * drive agents via direct invocation rather than cross-agent channels.
 *
 * @since v0.10 (Experimental)
 */
public final class NoopMessageBus implements MessageBus {

    @Override
    public Mono<Void> send(String fromAgentId, String toAgentId, Msg message) {
        return Mono.empty();
    }

    @Override
    public Flux<Msg> receive(String agentId) {
        return Flux.empty();
    }

    @Override
    public Mono<Void> broadcast(String fromAgentId, Msg message) {
        return Mono.empty();
    }
}
