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
package io.kairo.api.session;

import io.kairo.api.Experimental;
import io.kairo.api.agent.IterationCheckpointStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * SPI for per-session storage isolation. Each session gets its own directory under a workspace
 * root, containing checkpoints, iterations, and application-specific files.
 *
 * <p>Implementations must ensure that sessions do not share checkpoint or state files — the primary
 * motivation is preventing cross-session data leakage when multiple sessions point to the same
 * workspace directory.
 *
 * <p>Applications can extend this by adding custom files under {@link #sessionDir(String)} (e.g.,
 * plan files, snapshot refs).
 */
@Experimental("Session storage SPI; introduced in v1.3.0")
public interface SessionStorageProvider {

    /**
     * Ensure the session directory and its subdirectories exist.
     *
     * @param sessionId the unique session identifier
     */
    void ensureSession(String sessionId);

    /**
     * Get an {@link IterationCheckpointStore} scoped to the given session. Each session gets its
     * own independent checkpoint store so iterations from one session never leak into another.
     *
     * @param sessionId the unique session identifier
     * @return a checkpoint store that reads/writes under the session's directory
     */
    IterationCheckpointStore checkpointStore(String sessionId);

    /**
     * Get the root directory for a session's storage. Applications can place custom files here
     * (plan.md, snapshot.ref, etc.) beyond what the framework manages.
     *
     * @param sessionId the unique session identifier
     * @return the session directory path
     */
    Path sessionDir(String sessionId);

    /**
     * Garbage-collect old session directories. Retains the most recent {@code maxSessions} sessions
     * and any session accessed within {@code maxAge}. Deletes the rest.
     *
     * @param maxSessions maximum number of sessions to retain
     * @param maxAge maximum age of retained sessions
     */
    void gc(int maxSessions, Duration maxAge);

    /**
     * Detect and migrate legacy storage layout (files in the root session directory instead of
     * per-session subdirectories).
     *
     * @return the synthetic session ID assigned to the migrated data, or empty if no legacy found
     */
    Optional<String> migrateLegacy();
}
