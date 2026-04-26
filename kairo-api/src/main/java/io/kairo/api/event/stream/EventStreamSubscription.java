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
package io.kairo.api.event.stream;

import io.kairo.api.Experimental;
import io.kairo.api.event.KairoEvent;
import reactor.core.publisher.Flux;

/**
 * Active subscription handle returned by the event-stream service after an authorization pass. The
 * subscriber consumes {@link #events()}; the transport layer calls {@link #cancel()} when the
 * remote consumer disconnects.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("EventStream SPI — contract may change in v0.10")
public interface EventStreamSubscription {

    /** Unique subscription identifier assigned by the event-stream service. */
    String id();

    /** Filtered, back-pressured event flux consumed by the transport layer. */
    Flux<KairoEvent> events();

    /** Release the subscription. Idempotent. */
    void cancel();

    /** Whether the subscription is still forwarding events. */
    boolean isActive();
}
