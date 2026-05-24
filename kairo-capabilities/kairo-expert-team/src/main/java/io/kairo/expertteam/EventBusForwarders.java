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
package io.kairo.expertteam;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.team.TeamEvent;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Factory for lightweight {@link KairoEventBus} implementations that forward {@link TeamEvent}
 * payloads to a {@link Consumer}.
 *
 * <p>Used by downstream modules (e.g. kairo-code-core) that need to bridge team lifecycle events
 * into their own rendering layer without depending directly on the {@code kairo-api} event
 * interfaces.
 */
public final class EventBusForwarders {

    private EventBusForwarders() {}

    /**
     * Create a {@link KairoEventBus} that unwraps {@link TeamEvent} payloads and forwards them to
     * the given consumer.
     *
     * @param consumer receiver for team events (must never throw)
     * @return a forwarding event bus
     */
    public static KairoEventBus toKairoEventBus(Consumer<TeamEvent> consumer) {
        return new ForwardingEventBus(consumer);
    }

    private static final class ForwardingEventBus implements KairoEventBus {

        private final Sinks.Many<KairoEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        private final Consumer<TeamEvent> consumer;

        ForwardingEventBus(Consumer<TeamEvent> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void publish(KairoEvent event) {
            if (event.payload() instanceof TeamEvent teamEvent) {
                try {
                    consumer.accept(teamEvent);
                } catch (Exception ignored) {
                    // KairoEventBus contract: must never throw
                }
            }
            sink.tryEmitNext(event);
        }

        @Override
        public Flux<KairoEvent> subscribe() {
            return sink.asFlux();
        }

        @Override
        public Flux<KairoEvent> subscribe(String domain) {
            return sink.asFlux().filter(e -> domain.equals(e.domain()));
        }
    }
}
