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
package io.kairo.api.evolution;

import io.kairo.api.Experimental;
import java.time.Instant;
import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Sidecar persistence SPI for {@link SkillTelemetry}. Separate from {@link EvolvedSkillStore} so
 * the curator can mutate counters and lifecycle state without rewriting (and risk corrupting) the
 * skill body itself.
 *
 * <p>Implementations must serialize record-modify-write across processes (the file-backed default
 * uses an exclusive lock); all bumps below are individually atomic.
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change before v1.2.0 stabilization")
public interface SkillTelemetryStore {

    /** Fetch the telemetry record for {@code skillName}, or empty if no record exists yet. */
    Mono<Optional<SkillTelemetry>> get(String skillName);

    /** List telemetry for every skill the store knows about. */
    Flux<SkillTelemetry> list();

    /**
     * Persist {@code telemetry}, overwriting any existing record for the same {@link
     * SkillTelemetry#skillName()}. Returns the saved record.
     */
    Mono<SkillTelemetry> save(SkillTelemetry telemetry);

    /**
     * Hard-remove the telemetry record. The curator never calls this — operators do, only when a
     * skill is being permanently pruned (e.g. test fixture cleanup).
     */
    Mono<Void> delete(String skillName);

    // ----- Curator-friendly convenience bumps. Default impls do read-modify-write atop save(). ---

    /**
     * Increment {@link SkillTelemetry#useCount} and update {@link SkillTelemetry#lastUsedAt}. If no
     * record exists, seeds an {@link SkillProvenance#AGENT_CREATED} record at {@code at} first.
     */
    default Mono<SkillTelemetry> recordUse(String skillName, Instant at) {
        return upsert(
                skillName, at, telemetry -> telemetry.withUse(at), SkillProvenance.AGENT_CREATED);
    }

    /**
     * Increment {@link SkillTelemetry#viewCount} and update {@link SkillTelemetry#lastViewedAt}.
     */
    default Mono<SkillTelemetry> recordView(String skillName, Instant at) {
        return upsert(
                skillName, at, telemetry -> telemetry.withView(at), SkillProvenance.AGENT_CREATED);
    }

    /**
     * Increment {@link SkillTelemetry#patchCount} and update {@link SkillTelemetry#lastPatchedAt}.
     */
    default Mono<SkillTelemetry> recordPatch(String skillName, Instant at) {
        return upsert(
                skillName, at, telemetry -> telemetry.withPatch(at), SkillProvenance.AGENT_CREATED);
    }

    /** Set or update the pin flag (opt-out from all auto-transitions). */
    default Mono<SkillTelemetry> setPinned(String skillName, boolean pinned, Instant at) {
        return upsert(
                skillName,
                at,
                telemetry -> telemetry.withPinned(pinned),
                SkillProvenance.AGENT_CREATED);
    }

    /** Transition to a new lifecycle state (reactivate, mark stale, etc.). */
    default Mono<SkillTelemetry> setState(String skillName, SkillLifecycleState state, Instant at) {
        return upsert(
                skillName,
                at,
                telemetry -> telemetry.withState(state),
                SkillProvenance.AGENT_CREATED);
    }

    /**
     * Archive the skill, recording the timestamp and (optionally) the umbrella that absorbed its
     * content. Pass {@code null} for {@code absorbedInto} when archiving without consolidation.
     */
    default Mono<SkillTelemetry> archive(String skillName, String absorbedInto, Instant at) {
        return upsert(
                skillName,
                at,
                telemetry -> telemetry.archived(at, absorbedInto),
                SkillProvenance.AGENT_CREATED);
    }

    /**
     * Atomically apply {@code mutator} to the current telemetry, seeding a new {@link
     * SkillProvenance#AGENT_CREATED} record at {@code at} if none exists. Provided as a default so
     * custom implementations can override with a single round-trip if their backend supports it.
     */
    default Mono<SkillTelemetry> upsert(
            String skillName,
            Instant at,
            java.util.function.UnaryOperator<SkillTelemetry> mutator,
            SkillProvenance seedProvenance) {
        return get(skillName)
                .flatMap(
                        existing -> {
                            SkillTelemetry base =
                                    existing.orElseGet(
                                            () ->
                                                    SkillTelemetry.initial(
                                                            skillName, seedProvenance, at));
                            return save(mutator.apply(base));
                        });
    }
}
