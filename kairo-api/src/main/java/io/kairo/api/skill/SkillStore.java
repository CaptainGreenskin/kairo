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
package io.kairo.api.skill;

import io.kairo.api.Experimental;
import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Unified reactive Skill storage SPI introduced in v0.10. Intended to converge the split between
 * the classic {@link SkillRegistry} (synchronous, static skills) and {@code EvolvedSkillStore}
 * (reactive, runtime-generated skills) once the {@code kairo-skill} module extraction lands.
 *
 * <p>Implementations are free to back their storage with in-memory maps, JDBC, or vector stores.
 * The SPI intentionally stays minimal: higher-level features (versioning, trust levels, provenance)
 * are carried on the stored VO object rather than in the SPI surface.
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Skill subsystem unification — API may change in v0.11")
public interface SkillStore {

    /** Persist a skill definition; returns the stored value. */
    Mono<SkillDefinition> save(SkillDefinition skill);

    /** Look up a skill by name. */
    Mono<Optional<SkillDefinition>> get(String name);

    /** Remove a skill by name; completes normally even when no skill existed. */
    Mono<Void> delete(String name);

    /** List every skill currently stored. */
    Mono<List<SkillDefinition>> list();
}
