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
package io.kairo.multiagent.team;

import io.kairo.core.memory.structured.MemoryFile;
import io.kairo.core.memory.structured.MemoryType;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks explored code paths and findings across a team to prevent duplicate exploration. Backed by
 * {@link TeamMemoryStore}: each tracked path is persisted as a PROJECT memory so all agents in the
 * team can see it.
 *
 * <p>Usage pattern:
 *
 * <pre>{@code
 * ExplorationTracker tracker = new ExplorationTracker(store);
 * if (!tracker.isExplored("src/main/java/com/example/AuthService.java")) {
 *     // explore the file
 *     tracker.markExplored("src/main/java/com/example/AuthService.java", "No issues found");
 * }
 * }</pre>
 */
public class ExplorationTracker {

    private static final String MEMORY_PREFIX = "explored-";
    private static final String DESCRIPTION_PREFIX = "Explored path: ";

    private final TeamMemoryStore store;

    public ExplorationTracker(TeamMemoryStore store) {
        this.store = store;
    }

    /**
     * Mark a code path as explored with an optional finding summary.
     *
     * @param path the file or directory path that was explored
     * @param finding summary of what was found (may be empty)
     */
    public void markExplored(String path, String finding) {
        String slug = toSlug(path);
        String body =
                "**Path:** `"
                        + path
                        + "`\n\n"
                        + "**Finding:** "
                        + (finding.isBlank() ? "No notable findings" : finding);
        MemoryFile file =
                new MemoryFile(
                        slug, DESCRIPTION_PREFIX + path, MemoryType.PROJECT, body, Instant.now());
        store.write(file);
    }

    /**
     * Check if a path has already been explored by any agent in the team.
     *
     * @param path the file or directory path to check
     * @return true if already explored
     */
    public boolean isExplored(String path) {
        String slug = toSlug(path);
        return store.read(slug) != null;
    }

    /**
     * Get the finding for a previously explored path.
     *
     * @param path the explored path
     * @return the memory file, or null if not explored
     */
    public MemoryFile getFinding(String path) {
        return store.read(toSlug(path));
    }

    /**
     * List all explored paths in this team.
     *
     * @return set of explored path slugs
     */
    public Set<String> allExploredPaths() {
        List<MemoryFile> all = store.listAll();
        Set<String> paths = new LinkedHashSet<>();
        for (MemoryFile file : all) {
            if (file.name().startsWith(MEMORY_PREFIX)) {
                paths.add(file.name());
            }
        }
        return paths;
    }

    /**
     * Remove the exploration marker for a path, allowing re-exploration.
     *
     * @param path the path to un-mark
     * @return true if the marker existed and was removed
     */
    public boolean clearExplored(String path) {
        return store.delete(toSlug(path));
    }

    static String toSlug(String path) {
        String normalized =
                path.replace('\\', '/')
                        .replaceAll("[^a-zA-Z0-9/._-]", "")
                        .replace('/', '-')
                        .replace('.', '-')
                        .toLowerCase();
        if (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("-")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return MEMORY_PREFIX + normalized;
    }
}
