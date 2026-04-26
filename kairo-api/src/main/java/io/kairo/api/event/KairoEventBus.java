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
package io.kairo.api.event;

import io.kairo.api.Experimental;
import reactor.core.publisher.Flux;

/**
 * Pub/sub facade that unifies every Kairo observability domain (execution, evolution, security,
 * team) behind a single subscription point.
 *
 * <p>The bus is in-process by default. Enterprise deployments can bridge subscribers to external
 * systems (OTel, Kafka, JMS) without each emitter learning about the transport.
 *
 * <p>Publishers should prefer {@link KairoEvent#wrap(String, String, Object)} to preserve the
 * strongly-typed payload for downstream consumers that still require domain fidelity.
 *
 * @since v0.10 (Experimental)
 */
@Experimental("KairoEventBus — contract may change in v0.11")
public interface KairoEventBus {

    /**
     * Publish an event. Must never throw; implementations log and swallow internal failures so a
     * failing sink never breaks the primary execution flow.
     */
    void publish(KairoEvent event);

    /** Subscribe to all events across every domain. */
    Flux<KairoEvent> subscribe();

    /**
     * Subscribe to events from a specific domain only (case-sensitive match against {@link
     * KairoEvent#domain()}).
     */
    Flux<KairoEvent> subscribe(String domain);
}
