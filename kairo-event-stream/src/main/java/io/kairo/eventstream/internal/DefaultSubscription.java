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
package io.kairo.eventstream.internal;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.stream.EventStreamSubscription;
import io.kairo.eventstream.EventStreamRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Default implementation of {@link EventStreamSubscription}. Relies on a unicast sink so cancel
 * propagates a completion signal to downstream transports and terminates the underlying pipeline.
 */
public final class DefaultSubscription implements EventStreamSubscription {

    private final String id;
    private final Flux<KairoEvent> events;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Consumer<String> onCancel;

    public DefaultSubscription(
            String id,
            Flux<KairoEvent> source,
            Sinks.Empty<Void> cancelSignal,
            EventStreamRegistry registry) {
        this.id = id;
        this.onCancel =
                subId -> {
                    cancelSignal.tryEmitEmpty();
                    registry.unregister(subId);
                };
        this.events =
                source.takeUntilOther(cancelSignal.asMono()).doFinally(sig -> active.set(false));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Flux<KairoEvent> events() {
        return events;
    }

    @Override
    public void cancel() {
        if (active.compareAndSet(true, false)) {
            onCancel.accept(id);
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }
}
