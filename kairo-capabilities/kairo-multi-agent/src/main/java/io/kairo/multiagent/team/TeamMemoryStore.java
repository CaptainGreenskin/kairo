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

import io.kairo.core.memory.structured.MemoryDirectoryManager;
import io.kairo.core.memory.structured.MemoryFile;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Team-scoped memory store backed by a {@link MemoryDirectoryManager} rooted at {@code
 * .kairo/teams/{teamName}/memory/}.
 *
 * <p>All agents in the same team share this memory directory, providing automatic synchronization
 * through the file system. Thread-safe via the underlying MemoryDirectoryManager locks.
 */
public class TeamMemoryStore {

    private static final String TEAMS_DIR = ".kairo/teams";
    private static final String MEMORY_DIR = "memory";

    private static final ConcurrentHashMap<String, TeamMemoryStore> INSTANCES =
            new ConcurrentHashMap<>();

    private final String teamName;
    private final MemoryDirectoryManager manager;

    TeamMemoryStore(String teamName, Path teamMemoryDir) {
        this.teamName = teamName;
        this.manager = new MemoryDirectoryManager(teamMemoryDir);
    }

    /**
     * Get or create a shared TeamMemoryStore for the given workspace and team name.
     *
     * @param workspaceRoot the workspace root path
     * @param teamName the team name
     * @return shared instance
     */
    public static TeamMemoryStore forTeam(Path workspaceRoot, String teamName) {
        Path teamMemoryDir = workspaceRoot.resolve(TEAMS_DIR).resolve(teamName).resolve(MEMORY_DIR);
        String key = workspaceRoot.toAbsolutePath().normalize() + "::" + teamName;
        return INSTANCES.computeIfAbsent(key, k -> new TeamMemoryStore(teamName, teamMemoryDir));
    }

    /** Clear the shared instance cache. Intended for testing. */
    public static void clearInstances() {
        INSTANCES.clear();
    }

    public String teamName() {
        return teamName;
    }

    public void write(MemoryFile file) {
        manager.write(file);
    }

    public MemoryFile read(String name) {
        return manager.read(name);
    }

    public boolean delete(String name) {
        return manager.delete(name);
    }

    public List<MemoryFile> listAll() {
        return manager.listAll();
    }

    public List<MemoryFile> search(String query, int limit) {
        return manager.search(query, limit);
    }

    public String loadIndex() {
        return manager.loadIndex();
    }

    public void regenerateIndex() {
        manager.regenerateIndex();
    }

    /** Returns the underlying directory path for this team's memory. */
    public Path memoryDir() {
        return manager.getMemoryDir();
    }
}
