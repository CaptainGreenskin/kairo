/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.eventstream;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

final class FakeKairoEventBus implements KairoEventBus {

    private final Sinks.Many<KairoEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer(1024, false);

    @Override
    public void publish(KairoEvent event) {
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
