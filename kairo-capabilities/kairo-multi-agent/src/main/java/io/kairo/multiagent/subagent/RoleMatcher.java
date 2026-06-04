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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Matches tasks to expert roles based on keyword-extracted features and structured role
 * capabilities.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * RoleMatcher matcher = new RoleMatcher(registry);
 * List<RoleMatchResult> ranked = matcher.match("Fix auth bug in Spring REST API");
 * List<RoleMatchResult> lineup = matcher.selectLineup("Design a caching layer", 3);
 * }</pre>
 */
public final class RoleMatcher {

    private static final double MIN_RELEVANCE_THRESHOLD = 0.3;

    private final ExpertRoleRegistry registry;

    public RoleMatcher(ExpertRoleRegistry registry) {
        this.registry = registry;
    }

    /**
     * Score all registered roles against the given task description, sorted by relevance
     * (descending).
     */
    public List<RoleMatchResult> match(String taskDescription) {
        TaskFeatures features = TaskFeatureExtractor.extract(taskDescription);
        return matchFeatures(features, registry.allProfiles());
    }

    /**
     * Score the given candidate profiles against the task description, sorted by relevance
     * (descending).
     */
    public List<RoleMatchResult> match(
            String taskDescription, Collection<ExpertProfile> candidates) {
        TaskFeatures features = TaskFeatureExtractor.extract(taskDescription);
        return matchFeatures(features, candidates);
    }

    /**
     * Select the top-N most relevant roles for the given task. Always includes at least one role.
     * Roles below the minimum relevance threshold are excluded unless that would leave no roles.
     *
     * @param taskDescription the task goal text
     * @param maxRoles maximum number of roles to include
     * @return ranked list of selected roles (size in [1, maxRoles])
     */
    public List<RoleMatchResult> selectLineup(String taskDescription, int maxRoles) {
        if (maxRoles < 1) {
            throw new IllegalArgumentException("maxRoles must be >= 1");
        }
        List<RoleMatchResult> ranked = match(taskDescription);
        if (ranked.isEmpty()) {
            return List.of();
        }

        List<RoleMatchResult> selected = new ArrayList<>();
        for (RoleMatchResult r : ranked) {
            if (selected.size() >= maxRoles) {
                break;
            }
            if (selected.isEmpty() || r.score() >= MIN_RELEVANCE_THRESHOLD) {
                selected.add(r);
            }
        }

        return List.copyOf(selected);
    }

    private List<RoleMatchResult> matchFeatures(
            TaskFeatures features, Collection<ExpertProfile> candidates) {
        List<RoleMatchResult> results = new ArrayList<>(candidates.size());
        for (ExpertProfile profile : candidates) {
            double score = profile.capabilities().score(features);
            results.add(new RoleMatchResult(profile.roleId(), score, profile));
        }
        Collections.sort(results);
        return List.copyOf(results);
    }
}
