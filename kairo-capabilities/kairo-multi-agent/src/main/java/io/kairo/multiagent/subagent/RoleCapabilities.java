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
package io.kairo.multiagent.subagent;

import java.util.Set;

/**
 * Structured capabilities describing what a role is good at. Used by {@link RoleMatcher} to compute
 * relevance scores against {@link TaskFeatures}.
 *
 * @param languages programming languages this role specializes in (empty = language-agnostic)
 * @param frameworks frameworks this role handles well
 * @param domains task domains this role covers (e.g., "testing", "security")
 * @param actions action types this role performs (e.g., "implement", "review", "design")
 */
public record RoleCapabilities(
        Set<String> languages, Set<String> frameworks, Set<String> domains, Set<String> actions) {

    public static final RoleCapabilities EMPTY =
            new RoleCapabilities(Set.of(), Set.of(), Set.of(), Set.of());

    public RoleCapabilities {
        languages = languages != null ? Set.copyOf(languages) : Set.of();
        frameworks = frameworks != null ? Set.copyOf(frameworks) : Set.of();
        domains = domains != null ? Set.copyOf(domains) : Set.of();
        actions = actions != null ? Set.copyOf(actions) : Set.of();
    }

    /**
     * Compute a relevance score [0.0, 1.0] of this role's capabilities against the given task
     * features. Higher means better match.
     *
     * <p>Scoring weights: actions 0.4, domains 0.3, languages 0.15, frameworks 0.15. A role with
     * empty capabilities in a dimension is treated as "can handle anything" in that dimension
     * (wildcard), receiving 50% of the weight for that dimension.
     */
    public double score(TaskFeatures features) {
        if (features.isEmpty()) {
            return 0.5; // no features = neutral match
        }

        double actionScore = dimensionScore(this.actions, features.actions());
        double domainScore = dimensionScore(this.domains, features.domains());
        double langScore = dimensionScore(this.languages, features.languages());
        double frameworkScore = dimensionScore(this.frameworks, features.frameworks());

        return actionScore * 0.4 + domainScore * 0.3 + langScore * 0.15 + frameworkScore * 0.15;
    }

    private static double dimensionScore(Set<String> capabilities, Set<String> features) {
        if (features.isEmpty()) {
            return 0.5; // no features in this dimension = neutral
        }
        if (capabilities.isEmpty()) {
            return 0.5; // wildcard role = partial credit
        }
        long overlap = features.stream().filter(capabilities::contains).count();
        return (double) overlap / features.size();
    }
}
