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
package io.kairo.api.context;

/**
 * A pluggable context source that provides information to be injected into the LLM prompt.
 *
 * <p>Implementations collect contextual information from various sources (system info, git status,
 * project files, etc.) and return it as a string that will be assembled into the prompt.
 *
 * <p>Sources are ordered by {@link #priority()} — lower values are considered more important and
 * are less likely to be dropped when context length is constrained.
 */
public interface ContextSource {

    /**
     * A human-readable name for this source, used in logging and debugging.
     *
     * @return the source name (never null)
     */
    String getName();

    /**
     * Priority for ordering among sources. Lower values = higher importance. Recommended ranges:
     *
     * <ul>
     *   <li>0–10: Critical (date, core system info)
     *   <li>10–30: Important (project structure, git status)
     *   <li>30–50: Supplementary (user-defined context)
     * </ul>
     *
     * @return the priority value
     */
    int priority();

    /**
     * Whether this source is currently active and should be polled. Inactive sources are skipped
     * during context assembly.
     *
     * @return true if the source should be polled
     */
    boolean isActive();

    /**
     * Collect context content from this source.
     *
     * <p>Implementations should be lightweight and avoid throwing exceptions. If collection fails,
     * return an empty string rather than propagating errors.
     *
     * @return the collected context content, or empty string if unavailable
     */
    String collect();

    /**
     * Create a simple ContextSource from a name, priority, and supplier.
     *
     * <p>Convenience factory for quick registration without creating a full class:
     *
     * <pre>{@code
     * ContextSource gitStatus = ContextSource.of("git-status", 15, () -> {
     *     return runCommand("git status --short");
     * });
     * }</pre>
     *
     * @param name the source name
     * @param priority the priority
     * @param supplier the content supplier
     * @return a new ContextSource instance
     */
    static ContextSource of(
            String name, int priority, java.util.function.Supplier<String> supplier) {
        return new ContextSource() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public String collect() {
                return supplier.get();
            }

            @Override
            public String toString() {
                return "ContextSource[" + name + "]";
            }
        };
    }
}
