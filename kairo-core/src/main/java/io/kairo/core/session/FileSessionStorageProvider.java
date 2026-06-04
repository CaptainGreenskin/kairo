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
package io.kairo.core.session;

import io.kairo.api.agent.IterationCheckpointStore;
import io.kairo.api.session.SessionStorageProvider;
import io.kairo.core.agent.checkpoint.JsonFileIterationCheckpointStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-based implementation of {@link SessionStorageProvider}. Stores each session under {@code
 * {rootDir}/{sessionId}/} with a {@code iterations/} subdirectory for checkpoints.
 *
 * <p>The root directory is typically {@code {workingDir}/.kairo-session/}.
 */
public class FileSessionStorageProvider implements SessionStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(FileSessionStorageProvider.class);
    private static final String LEGACY_DIR = "_legacy";
    private static final int DEFAULT_MAX_SESSIONS = 10;
    private static final Duration DEFAULT_MAX_AGE = Duration.ofDays(30);

    private final Path rootDir;

    /**
     * @param rootDir the root session storage directory (e.g., {@code
     *     {workingDir}/.kairo-session/})
     */
    public FileSessionStorageProvider(Path rootDir) {
        this.rootDir = rootDir;
    }

    @Override
    public void ensureSession(String sessionId) {
        try {
            Files.createDirectories(sessionDir(sessionId).resolve("iterations"));
        } catch (IOException e) {
            log.warn("Failed to create session directory for {}: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public IterationCheckpointStore checkpointStore(String sessionId) {
        Path iterDir = sessionDir(sessionId).resolve("iterations");
        return new JsonFileIterationCheckpointStore(iterDir, new SessionSerializer());
    }

    @Override
    public Path sessionDir(String sessionId) {
        return rootDir.resolve(sessionId);
    }

    /** The root directory containing all session subdirectories. */
    public Path rootDir() {
        return rootDir;
    }

    @Override
    public void gc(int maxSessions, Duration maxAge) {
        if (!Files.isDirectory(rootDir)) return;

        try (Stream<Path> dirs = Files.list(rootDir)) {
            List<Path> sessionDirs =
                    dirs.filter(Files::isDirectory)
                            .filter(p -> !p.getFileName().toString().equals(LEGACY_DIR))
                            .filter(p -> !p.getFileName().toString().startsWith("."))
                            .filter(p -> !p.getFileName().toString().equals("iterations"))
                            .sorted(
                                    Comparator.comparingLong(
                                                    (Path p) -> {
                                                        try {
                                                            return Files.getLastModifiedTime(p)
                                                                    .toMillis();
                                                        } catch (IOException e) {
                                                            return 0L;
                                                        }
                                                    })
                                            .reversed())
                            .toList();

            Instant cutoff = Instant.now().minus(maxAge);
            int kept = 0;

            for (Path dir : sessionDirs) {
                Instant lastModified;
                try {
                    lastModified = Files.getLastModifiedTime(dir).toInstant();
                } catch (IOException e) {
                    lastModified = Instant.EPOCH;
                }

                if (lastModified.isAfter(cutoff) || kept < maxSessions) {
                    kept++;
                } else {
                    deleteRecursively(dir);
                    log.debug("GC removed session dir: {}", dir.getFileName());
                }
            }
        } catch (IOException e) {
            log.warn("Session GC failed: {}", e.getMessage());
        }
    }

    /** Convenience: GC with default limits (10 sessions, 30 days). */
    public void gc() {
        gc(DEFAULT_MAX_SESSIONS, DEFAULT_MAX_AGE);
    }

    @Override
    public Optional<String> migrateLegacy() {
        Path legacyCheckpoint = rootDir.resolve("checkpoint.json");
        Path legacyPhase = rootDir.resolve("phase.txt");
        Path legacyIterations = rootDir.resolve("iterations");

        boolean hasLegacy =
                Files.exists(legacyCheckpoint)
                        || Files.exists(legacyPhase)
                        || (Files.isDirectory(legacyIterations)
                                && !isSessionSubdir(legacyIterations));

        if (!hasLegacy) return Optional.empty();

        String syntheticId = "legacy-session";
        Path legacyDir = rootDir.resolve(LEGACY_DIR).resolve(syntheticId);

        try {
            Files.createDirectories(legacyDir);
            if (Files.exists(legacyCheckpoint)) {
                Files.move(legacyCheckpoint, legacyDir.resolve("checkpoint.json"));
            }
            if (Files.exists(legacyPhase)) {
                Files.move(legacyPhase, legacyDir.resolve("phase.txt"));
            }
            if (Files.isDirectory(legacyIterations) && !isSessionSubdir(legacyIterations)) {
                Files.move(legacyIterations, legacyDir.resolve("iterations"));
            }
            log.info("Migrated legacy session state to {}", legacyDir);
            return Optional.of(LEGACY_DIR + "/" + syntheticId);
        } catch (IOException e) {
            log.warn("Failed to migrate legacy session state: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isSessionSubdir(Path dir) {
        // Legacy iterations/ is directly under rootDir; session iterations/ is under
        // rootDir/{sessionId}/. Check if parent is rootDir (legacy) or a UUID-like subdir.
        return !dir.getParent().equals(rootDir);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
        } catch (IOException ignored) {
        }
    }
}
