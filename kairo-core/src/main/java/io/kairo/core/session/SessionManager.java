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

import io.kairo.api.memory.MemoryScope;
import io.kairo.core.memory.FileMemoryStore;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * High-level session management: save, load, list, and clean up agent conversation sessions.
 *
 * <p>Sessions are persisted via {@link FileMemoryStore} using the {@link MemoryScope#SESSION}
 * scope, and serialized using {@link SessionSerializer} with versioned JSON format.
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final FileMemoryStore store;
    private final SessionSerializer serializer;

    /**
     * Create a SessionManager with the given storage directory.
     *
     * @param storageDir the root directory for session file storage
     */
    public SessionManager(Path storageDir) {
        this.store = new FileMemoryStore(storageDir);
        this.serializer = new SessionSerializer();
    }

    /**
     * Create a SessionManager with an existing store and serializer.
     *
     * @param store the file memory store
     * @param serializer the session serializer
     */
    public SessionManager(FileMemoryStore store, SessionSerializer serializer) {
        this.store = store;
        this.serializer = serializer;
    }

    /**
     * Save a session snapshot.
     *
     * @param sessionId the session identifier
     * @param snapshot the snapshot to persist
     * @return a Mono completing when saved
     */
    public Mono<Void> saveSession(String sessionId, SessionSnapshot snapshot) {
        String json = serializer.serialize(snapshot);
        return store.saveRaw(sessionId, json, MemoryScope.SESSION)
                .doOnSuccess(v -> log.debug("Saved session {}", sessionId));
    }

    /**
     * Load a session snapshot by ID.
     *
     * @param sessionId the session identifier
     * @return a Mono emitting the snapshot, or empty if not found
     */
    public Mono<SessionSnapshot> loadSession(String sessionId) {
        return store.loadRaw(sessionId, MemoryScope.SESSION)
                .map(serializer::deserialize)
                .doOnNext(
                        s ->
                                log.debug(
                                        "Loaded session {} with {} turns",
                                        sessionId,
                                        s.turnCount()));
    }

    /**
     * List all saved sessions with lightweight metadata (no full message payloads).
     *
     * @return a Flux of session metadata
     */
    public Flux<SessionMetadata> listSessions() {
        return store.listKeys(MemoryScope.SESSION)
                .flatMap(
                        key ->
                                store.loadRaw(key, MemoryScope.SESSION)
                                        .map(serializer::extractMetadata)
                                        .onErrorResume(
                                                e -> {
                                                    log.warn(
                                                            "Failed to read session metadata for {}: {}",
                                                            key,
                                                            e.getMessage());
                                                    return Mono.empty();
                                                }));
    }

    /**
     * Delete a session by ID.
     *
     * @param sessionId the session identifier
     * @return a Mono emitting true if deleted, false if not found
     */
    public Mono<Boolean> deleteSession(String sessionId) {
        return store.deleteRaw(sessionId, MemoryScope.SESSION)
                .doOnNext(
                        deleted -> {
                            if (deleted) {
                                log.debug("Deleted session {}", sessionId);
                            }
                        });
    }

    /**
     * Clean up sessions older than the specified TTL based on their last update time.
     *
     * @param ttl the maximum age of sessions to keep
     * @return a Mono emitting the count of deleted sessions
     */
    public Mono<Integer> cleanupExpired(Duration ttl) {
        Instant cutoff = Instant.now().minus(ttl);
        return listSessions()
                .filter(meta -> meta.updatedAt().isBefore(cutoff))
                .flatMap(meta -> deleteSession(meta.sessionId()).thenReturn(meta.sessionId()))
                .doOnNext(id -> log.debug("Cleaned up expired session {}", id))
                .collectList()
                .map(
                        deleted -> {
                            log.info(
                                    "Session cleanup: removed {} expired sessions", deleted.size());
                            return deleted.size();
                        });
    }

    /**
     * Get the underlying file memory store.
     *
     * @return the store
     */
    public FileMemoryStore getStore() {
        return store;
    }
}
