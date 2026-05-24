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
package io.kairo.expertteam.role;

import java.util.Set;

/**
 * Structured features extracted from a task description. Used by {@link RoleMatcher} to score role
 * relevance.
 *
 * @param languages programming languages detected (e.g., "java", "python", "go")
 * @param frameworks frameworks/libraries detected (e.g., "spring", "react", "django")
 * @param domains task domains detected (e.g., "testing", "security", "devops", "database")
 * @param actions action types detected (e.g., "implement", "review", "test", "design", "debug")
 */
public record TaskFeatures(
        Set<String> languages, Set<String> frameworks, Set<String> domains, Set<String> actions) {

    public TaskFeatures {
        languages = languages != null ? Set.copyOf(languages) : Set.of();
        frameworks = frameworks != null ? Set.copyOf(frameworks) : Set.of();
        domains = domains != null ? Set.copyOf(domains) : Set.of();
        actions = actions != null ? Set.copyOf(actions) : Set.of();
    }

    public boolean isEmpty() {
        return languages.isEmpty()
                && frameworks.isEmpty()
                && domains.isEmpty()
                && actions.isEmpty();
    }
}
