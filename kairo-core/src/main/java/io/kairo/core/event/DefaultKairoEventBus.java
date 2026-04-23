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
package io.kairo.core.event;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Default in-process implementation of {@link KairoEventBus} backed by a multicast Reactor sink.
 *
 * <p>Uses {@code Sinks.many().multicast().onBackpressureBuffer()} so multiple concurrent
 * subscribers (OTel exporter, metrics, custom audit sink) can receive every event without blocking
 * the publisher. Events published when no subscribers are connected are dropped; this is
 * intentional — subscribers must attach before publishers start emitting.
 *
 * @since v0.10 (Experimental)
 */
public class DefaultKairoEventBus implements KairoEventBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultKairoEventBus.class);

    private final Sinks.Many<KairoEvent> sink;

    public DefaultKairoEventBus() {
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @Override
    public void publish(KairoEvent event) {
        if (event == null) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.debug(
                    "KairoEventBus dropped event (domain={}, type={}, result={})",
                    event.domain(),
                    event.eventType(),
                    result);
        }
    }

    @Override
    public Flux<KairoEvent> subscribe() {
        return sink.asFlux();
    }

    @Override
    public Flux<KairoEvent> subscribe(String domain) {
        if (domain == null || domain.isBlank()) {
            return subscribe();
        }
        return sink.asFlux().filter(event -> domain.equals(event.domain()));
    }
}
