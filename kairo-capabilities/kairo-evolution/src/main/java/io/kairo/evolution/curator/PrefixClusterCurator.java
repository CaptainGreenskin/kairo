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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Deterministic, offline {@link LlmSkillCurator} that clusters candidates by name prefix and
 * proposes {@link CuratorAction.MergeIntoUmbrella} for clusters of {@code >= minClusterSize}.
 *
 * <p>Picks the longest candidate in each cluster as the umbrella (Hermes' heuristic: the broadest
 * skill in the cluster is the most likely class-level umbrella). Useful as:
 *
 * <ul>
 *   <li>A unit-test fixture for {@link CuratorActionExecutor} and {@link
 *       UmbrellaConsolidationPlanner}.
 *   <li>A safe default when no model is wired up (zero cost, always available).
 *   <li>A first-pass to shrink the catalog before sending the residual to an LLM.
 * </ul>
 */
public final class PrefixClusterCurator implements LlmSkillCurator {

    private final int minClusterSize;

    public PrefixClusterCurator() {
        this(2);
    }

    public PrefixClusterCurator(int minClusterSize) {
        if (minClusterSize < 2) {
            throw new IllegalArgumentException("minClusterSize must be >= 2");
        }
        this.minClusterSize = minClusterSize;
    }

    @Override
    public Mono<List<CuratorAction>> propose(SkillCatalog catalog) {
        Map<String, List<EvolvedSkill>> clusters = clusterByPrefix(catalog);
        List<CuratorAction> out = new ArrayList<>();
        for (Map.Entry<String, List<EvolvedSkill>> e : clusters.entrySet()) {
            List<EvolvedSkill> members = e.getValue();
            if (members.size() < minClusterSize) {
                continue;
            }
            EvolvedSkill umbrella = pickUmbrella(members);
            List<String> siblings =
                    members.stream()
                            .map(EvolvedSkill::name)
                            .filter(n -> !n.equals(umbrella.name()))
                            .toList();
            out.add(
                    new CuratorAction.MergeIntoUmbrella(
                            umbrella.name(),
                            siblings,
                            "Prefix cluster '" + e.getKey() + "' (" + members.size() + " skills)"));
        }
        return Mono.just(out);
    }

    private Map<String, List<EvolvedSkill>> clusterByPrefix(SkillCatalog catalog) {
        Map<String, List<EvolvedSkill>> clusters = new LinkedHashMap<>();
        for (SkillCatalog.Entry entry : catalog.candidates()) {
            String prefix = firstWordOrSegment(entry.skill().name());
            if (prefix.isEmpty()) continue;
            clusters.computeIfAbsent(prefix, k -> new ArrayList<>()).add(entry.skill());
        }
        return clusters;
    }

    /** Heuristic: the candidate whose body is the largest is the most umbrella-like. */
    private EvolvedSkill pickUmbrella(List<EvolvedSkill> members) {
        return members.stream()
                .max(
                        Comparator.comparingInt(
                                s -> s.instructions() == null ? 0 : s.instructions().length()))
                .orElse(members.get(0));
    }

    /**
     * First {@code -}-separated segment of {@code name}; e.g. {@code "pr-fix-7"} → {@code "pr"}.
     */
    static String firstWordOrSegment(String name) {
        if (name == null) return "";
        int sep = name.indexOf('-');
        if (sep > 0) return name.substring(0, sep);
        sep = name.indexOf('_');
        if (sep > 0) return name.substring(0, sep);
        return name;
    }
}
