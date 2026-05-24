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

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * Minimal in-memory {@link KairoEventBus} used by the TCK to assert event order and cardinality.
 *
 * <p>Single-writer / single-reader semantics: not thread-safe for concurrent publication from
 * multiple threads. Adequate for deterministic unit tests that exercise the coordinator
 * sequentially.
 *
 * @since v0.10 (Experimental)
 */
public final class RecordingEventBus implements KairoEventBus {

    private final List<KairoEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(KairoEvent event) {
        if (event == null) {
            return;
        }
        events.add(event);
    }

    @Override
    public Flux<KairoEvent> subscribe() {
        return Flux.fromIterable(new ArrayList<>(events));
    }

    @Override
    public Flux<KairoEvent> subscribe(String domain) {
        return subscribe().filter(e -> domain == null || domain.equals(e.domain()));
    }

    /** Snapshot of every event published so far. */
    public List<KairoEvent> recorded() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    /** Snapshot filtered to the team domain. */
    public List<KairoEvent> recordedTeamEvents() {
        synchronized (events) {
            return events.stream().filter(e -> KairoEvent.DOMAIN_TEAM.equals(e.domain())).toList();
        }
    }

    /** Event-type names (in emission order) for the team domain. */
    public List<String> teamEventTypes() {
        return recordedTeamEvents().stream().map(KairoEvent::eventType).toList();
    }

    /** Clear the recorded events. */
    public void clear() {
        events.clear();
    }
}
