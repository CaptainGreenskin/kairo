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

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.SkillTelemetry;
import java.util.List;

/**
 * Snapshot of the candidate skills + their telemetry handed to the {@link LlmSkillCurator} for a
 * single review pass. Mirrors the structured catalog Hermes builds before invoking its review fork.
 *
 * @param candidates skills eligible for consolidation (already filtered: agent-created, not pinned,
 *     not archived)
 * @param immune skills the curator must skip (bundled / hub / pinned / manual) — included only so
 *     the LLM can see the surrounding landscape if it needs to reason about why a candidate is
 *     overlapping with an immune skill
 */
public record SkillCatalog(List<Entry> candidates, List<Entry> immune) {

    public SkillCatalog {
        candidates = List.copyOf(candidates);
        immune = List.copyOf(immune);
    }

    /**
     * A skill paired with its current telemetry. Telemetry may be {@code null} for never-touched
     * skills.
     */
    public record Entry(EvolvedSkill skill, SkillTelemetry telemetry) {}
}
