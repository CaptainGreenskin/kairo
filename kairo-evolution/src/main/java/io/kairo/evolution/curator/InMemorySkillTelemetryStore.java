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
package io.kairo.evolution.curator;

import io.kairo.api.evolution.SkillTelemetry;
import io.kairo.api.evolution.SkillTelemetryStore;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** In-memory {@link SkillTelemetryStore} — useful for tests and ephemeral usage. */
public final class InMemorySkillTelemetryStore implements SkillTelemetryStore {

    private final ConcurrentHashMap<String, SkillTelemetry> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Optional<SkillTelemetry>> get(String skillName) {
        return Mono.fromSupplier(() -> Optional.ofNullable(store.get(skillName)));
    }

    @Override
    public Flux<SkillTelemetry> list() {
        return Flux.fromIterable(store.values());
    }

    @Override
    public Mono<SkillTelemetry> save(SkillTelemetry telemetry) {
        return Mono.fromCallable(
                () -> {
                    store.put(telemetry.skillName(), telemetry);
                    return telemetry;
                });
    }

    @Override
    public Mono<Void> delete(String skillName) {
        return Mono.fromRunnable(() -> store.remove(skillName));
    }

    @Override
    public Mono<SkillTelemetry> upsert(
            String skillName,
            Instant at,
            UnaryOperator<SkillTelemetry> mutator,
            io.kairo.api.evolution.SkillProvenance seedProvenance) {
        return Mono.fromCallable(
                () -> {
                    SkillTelemetry result =
                            store.compute(
                                    skillName,
                                    (k, existing) -> {
                                        SkillTelemetry base =
                                                existing != null
                                                        ? existing
                                                        : SkillTelemetry.initial(
                                                                k, seedProvenance, at);
                                        return mutator.apply(base);
                                    });
                    return result;
                });
    }
}
