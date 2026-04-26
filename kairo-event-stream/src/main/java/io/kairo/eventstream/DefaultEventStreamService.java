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
package io.kairo.eventstream;

import io.kairo.api.Experimental;
import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.event.stream.EventStreamSubscription;
import io.kairo.api.event.stream.EventStreamSubscriptionRequest;
import io.kairo.api.event.stream.KairoEventStreamAuthorizer;
import io.kairo.api.event.stream.KairoEventStreamAuthorizer.AuthorizationDecision;
import io.kairo.eventstream.internal.DefaultSubscription;
import io.kairo.eventstream.internal.FluxBackpressureGuard;
import java.util.Objects;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Default {@link EventStreamService} implementation. Bridges {@link KairoEventBus} to filtered,
 * back-pressured subscriptions after consulting a {@link KairoEventStreamAuthorizer}.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("EventStream core — contract may change in v0.10")
public final class DefaultEventStreamService implements EventStreamService {

    private final KairoEventBus bus;
    private final KairoEventStreamAuthorizer authorizer;
    private final EventStreamRegistry registry;

    public DefaultEventStreamService(
            KairoEventBus bus,
            KairoEventStreamAuthorizer authorizer,
            EventStreamRegistry registry) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public EventStreamSubscription subscribe(EventStreamSubscriptionRequest request) {
        Objects.requireNonNull(request, "request");
        AuthorizationDecision decision = authorizer.authorize(request);
        if (!decision.allowed()) {
            throw new EventStreamAuthorizationException(decision.reason());
        }

        Flux<KairoEvent> filtered = bus.subscribe().filter(e -> request.filter().test(e));
        Flux<KairoEvent> guarded =
                FluxBackpressureGuard.apply(
                        filtered, request.backpressurePolicy(), request.bufferCapacity());

        String id = UUID.randomUUID().toString();
        Sinks.Empty<Void> cancelSignal = Sinks.empty();
        DefaultSubscription subscription =
                new DefaultSubscription(id, guarded, cancelSignal, registry);
        registry.register(subscription);
        return subscription;
    }

    @Override
    public int activeSubscriptionCount() {
        return registry.size();
    }
}
