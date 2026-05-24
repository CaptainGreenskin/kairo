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

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * The brain of the curator. Given a {@link SkillCatalog}, returns a list of {@link CuratorAction}s
 * to apply. Hosts plug in their preferred model wiring (Anthropic, GLM, local model, …); the
 * planner is agnostic.
 *
 * <p>Implementations should respect the hard rules baked into Hermes' {@code CURATOR_REVIEW_PROMPT}
 * — most importantly: never remove pinned skills, never use {@code use_count==0} as the sole reason
 * to archive, prefer one class-level umbrella over many narrow siblings.
 *
 * @see PrefixClusterCurator a deterministic fallback for tests / offline use that doesn't call an
 *     LLM
 */
@FunctionalInterface
public interface LlmSkillCurator {

    /**
     * @return proposed actions (may be empty if the LLM thinks nothing needs to change)
     */
    Mono<List<CuratorAction>> propose(SkillCatalog catalog);

    /** A curator that always proposes no changes — useful for tests and as a safe default. */
    static LlmSkillCurator noop() {
        return catalog -> Mono.just(List.of());
    }
}
