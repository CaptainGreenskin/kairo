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
package io.kairo.core.memory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kairo.api.exception.MemoryStoreException;
import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryQuery;
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.memory.MemoryStore;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * File-based implementation of {@link MemoryStore}.
 *
 * <p>Stores each {@link MemoryEntry} as a JSON file on disk using the layout: {@code
 * {storageDir}/{scope}/{id}.json}. Writes are atomic (write to {@code .tmp} then rename). All file
 * operations are protected by a {@link ReadWriteLock} for thread safety.
 */
public class FileMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(FileMemoryStore.class);
    private static final String JSON_SUFFIX = ".json";
    private static final String TMP_SUFFIX = ".json.tmp";

    /** Only allow alphanumeric, hyphen, underscore, and dot in storage keys. */
    private static final java.util.regex.Pattern SAFE_KEY_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_\\-\\.]{1,255}$");

    private final Path storageDir;
    private final ObjectMapper objectMapper;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Create a FileMemoryStore with the default storage directory ({@code .kairo/sessions/}). */
    public FileMemoryStore() {
        this(Path.of(".kairo", "sessions"));
    }

    /**
     * Create a FileMemoryStore with a custom storage directory.
     *
     * @param storageDir the root directory for file storage
     */
    public FileMemoryStore(Path storageDir) {
        this.storageDir = storageDir;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public Mono<MemoryEntry> save(MemoryEntry entry) {
        return Mono.fromCallable(
                () -> {
                    lock.writeLock().lock();
                    Path tmpFile = null;
                    try {
                        Path scopeDir = storageDir.resolve(entry.scope().name().toLowerCase());
                        Files.createDirectories(scopeDir);

                        Path targetFile = scopeDir.resolve(entry.id() + JSON_SUFFIX);
                        tmpFile = scopeDir.resolve(entry.id() + TMP_SUFFIX);

                        String json =
                                objectMapper
                                        .writerWithDefaultPrettyPrinter()
                                        .writeValueAsString(entry);
                        Files.writeString(tmpFile, json);
                        io.kairo.core.util.SafeFileOps.atomicMove(tmpFile, targetFile);

                        log.debug("Saved memory entry {} to {}", entry.id(), targetFile);
                        return entry;
                    } catch (Exception e) {
                        if (tmpFile != null) {
                            try {
                                Files.deleteIfExists(tmpFile);
                            } catch (IOException cleanupEx) {
                                log.warn("Failed to clean up temp file: {}", tmpFile);
                            }
                        }
                        throw e;
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    @Override
    public Mono<MemoryEntry> get(String id) {
        return Mono.fromCallable(
                        () -> {
                            lock.readLock().lock();
                            try {
                                for (MemoryScope scope : MemoryScope.values()) {
                                    Path file =
                                            storageDir
                                                    .resolve(scope.name().toLowerCase())
                                                    .resolve(id + JSON_SUFFIX);
                                    if (Files.exists(file)) {
                                        String json = Files.readString(file);
                                        return objectMapper.readValue(json, MemoryEntry.class);
                                    }
                                }
                                return null;
                            } finally {
                                lock.readLock().unlock();
                            }
                        })
                .flatMap(entry -> entry != null ? Mono.just(entry) : Mono.empty());
    }

    @Override
    public Flux<MemoryEntry> search(String query, MemoryScope scope) {
        String lowerQuery = query.toLowerCase();
        return list(scope)
                .filter(
                        e ->
                                e.content().toLowerCase().contains(lowerQuery)
                                        || e.tags().stream()
                                                .anyMatch(
                                                        tag ->
                                                                tag.toLowerCase()
                                                                        .contains(lowerQuery)));
    }

    @Override
    public Flux<MemoryEntry> search(String query, MemoryScope scope, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return search(query, scope);
        }
        String lowerQuery = query.toLowerCase();
        return list(scope)
                .filter(
                        e ->
                                (e.content().toLowerCase().contains(lowerQuery)
                                                || e.tags().stream()
                                                        .anyMatch(
                                                                tag ->
                                                                        tag.toLowerCase()
                                                                                .contains(
                                                                                        lowerQuery)))
                                        && e.tags() != null
                                        && e.tags().containsAll(tags));
    }

    /**
     * Ranked hybrid search over the persisted entries — the durable counterpart to {@link
     * InMemoryStore}'s in-memory ranking. Lexical relevance reuses {@link MemoryRelevanceScorer}
     * (weighted term-overlap + recency); vector relevance reuses {@link
     * InMemoryStore#cosineSimilarity}. The two candidate lists are fused per {@link
     * MemoryQuery.RankingMode} (RRF for {@code HYBRID}). No external full-text engine or new
     * dependency — pure-Java ranking over the JSON-backed store the assistant actually uses.
     */
    @Override
    public Flux<MemoryEntry> search(MemoryQuery query) {
        return Mono.fromCallable(() -> rankedSearch(query)).flatMapMany(Flux::fromIterable);
    }

    private List<MemoryEntry> rankedSearch(MemoryQuery query) {
        List<MemoryEntry> entries = loadAllEntries();
        Instant now = Instant.now();

        // Structured filters (mirror InMemoryStore semantics).
        List<MemoryEntry> filtered =
                entries.stream()
                        .filter(e -> query.agentId() == null || query.agentId().equals(e.agentId()))
                        .filter(
                                e ->
                                        query.tags() == null
                                                || query.tags().isEmpty()
                                                || (e.tags() != null
                                                        && e.tags().containsAll(query.tags())))
                        .filter(e -> e.importance() >= query.minImportance())
                        .filter(e -> query.from() == null || !e.timestamp().isBefore(query.from()))
                        .filter(e -> query.to() == null || !e.timestamp().isAfter(query.to()))
                        .toList();

        boolean hasKeyword = query.keyword() != null && !query.keyword().isBlank();
        boolean hasVector = query.queryVector() != null && query.queryVector().length > 0;

        // Lexical candidates: relevance-ranked when a keyword is present, else newest-first.
        List<MemoryEntry> lexical;
        if (hasKeyword) {
            String kw = query.keyword();
            // Inclusion is by term overlap (recency alone must not admit a non-matching entry);
            // ordering uses the full relevance score (term overlap + recency).
            lexical =
                    filtered.stream()
                            .filter(e -> MemoryRelevanceScorer.computeTermOverlap(e, kw) > 0.0)
                            .sorted(
                                    Comparator.comparingDouble(
                                                    (MemoryEntry e) ->
                                                            MemoryRelevanceScorer.score(e, kw, now))
                                            .reversed())
                            .limit(query.lexicalTopK())
                            .toList();
        } else {
            lexical =
                    filtered.stream()
                            .sorted(Comparator.comparing(MemoryEntry::timestamp).reversed())
                            .limit(query.lexicalTopK())
                            .toList();
        }

        // Vector candidates: cosine-ranked over entries that carry an embedding.
        List<MemoryEntry> vector = List.of();
        if (hasVector) {
            float[] qv = query.queryVector();
            vector =
                    filtered.stream()
                            .filter(e -> e.embedding() != null && e.embedding().length > 0)
                            .sorted(
                                    Comparator.comparingDouble(
                                                    (MemoryEntry e) ->
                                                            InMemoryStore.cosineSimilarity(
                                                                    qv, e.embedding()))
                                            .reversed())
                            .limit(query.vectorTopK())
                            .toList();
        }

        List<MemoryEntry> fused =
                switch (query.rankingMode()) {
                    case VECTOR_ONLY -> hasVector ? vector : lexical;
                    case LEXICAL_ONLY -> lexical;
                    case HYBRID -> {
                        if (hasVector && hasKeyword) {
                            yield rrf(lexical, vector);
                        }
                        // Only one signal present — fuse would just dilute it with the recency
                        // fallback list, so rank by whichever signal we actually have.
                        yield hasVector ? vector : lexical;
                    }
                };
        return fused.stream().limit(query.limit()).toList();
    }

    /** Reciprocal-rank fusion (k=60) of two ranked candidate lists, de-duplicated by id. */
    private static List<MemoryEntry> rrf(List<MemoryEntry> a, List<MemoryEntry> b) {
        final double k = 60.0;
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, MemoryEntry> byId = new LinkedHashMap<>();
        for (List<MemoryEntry> list : List.of(a, b)) {
            for (int i = 0; i < list.size(); i++) {
                MemoryEntry e = list.get(i);
                byId.putIfAbsent(e.id(), e);
                scores.merge(e.id(), 1.0 / (k + i + 1), Double::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(en -> byId.get(en.getKey()))
                .toList();
    }

    /** Load every persisted entry across all scopes (read-locked). */
    private List<MemoryEntry> loadAllEntries() {
        lock.readLock().lock();
        try {
            List<MemoryEntry> all = new ArrayList<>();
            for (MemoryScope scope : MemoryScope.values()) {
                Path scopeDir = storageDir.resolve(scope.name().toLowerCase());
                if (!Files.exists(scopeDir)) {
                    continue;
                }
                try (DirectoryStream<Path> stream =
                        Files.newDirectoryStream(scopeDir, "*" + JSON_SUFFIX)) {
                    for (Path file : stream) {
                        try {
                            all.add(
                                    objectMapper.readValue(
                                            Files.readString(file), MemoryEntry.class));
                        } catch (IOException e) {
                            log.warn(
                                    "Failed to read memory entry from {}: {}",
                                    file,
                                    e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    log.warn("Failed to scan scope dir {}: {}", scopeDir, e.getMessage());
                }
            }
            return all;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Mono<Void> delete(String id) {
        return Mono.fromRunnable(
                () -> {
                    lock.writeLock().lock();
                    try {
                        for (MemoryScope scope : MemoryScope.values()) {
                            Path file =
                                    storageDir
                                            .resolve(scope.name().toLowerCase())
                                            .resolve(id + JSON_SUFFIX);
                            if (Files.deleteIfExists(file)) {
                                log.debug("Deleted memory entry {} from {}", id, file);
                                break;
                            }
                        }
                    } catch (IOException e) {
                        throw new MemoryStoreException("Failed to delete memory entry: " + id, e);
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    @Override
    public Flux<MemoryEntry> list(MemoryScope scope) {
        return Mono.fromCallable(
                        () -> {
                            lock.readLock().lock();
                            try {
                                Path scopeDir = storageDir.resolve(scope.name().toLowerCase());
                                if (!Files.exists(scopeDir)) {
                                    return List.<MemoryEntry>of();
                                }
                                List<MemoryEntry> entries = new ArrayList<>();
                                try (DirectoryStream<Path> stream =
                                        Files.newDirectoryStream(scopeDir, "*" + JSON_SUFFIX)) {
                                    for (Path file : stream) {
                                        try {
                                            String json = Files.readString(file);
                                            entries.add(
                                                    objectMapper.readValue(
                                                            json, MemoryEntry.class));
                                        } catch (IOException e) {
                                            log.warn(
                                                    "Failed to read memory entry from {}: {}",
                                                    file,
                                                    e.getMessage());
                                        }
                                    }
                                }
                                entries.sort(
                                        Comparator.comparing(MemoryEntry::timestamp).reversed());
                                return entries;
                            } finally {
                                lock.readLock().unlock();
                            }
                        })
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Save a raw string value under the given key and scope.
     *
     * <p>This is a convenience method for session persistence where the caller manages
     * serialization. The value is written atomically to {@code {storageDir}/{scope}/{key}.json}.
     *
     * @param key the storage key
     * @param value the raw string value to store
     * @param scope the memory scope
     * @return a Mono completing when written
     */
    public Mono<Void> saveRaw(String key, String value, MemoryScope scope) {
        return Mono.fromRunnable(
                () -> {
                    validateKey(key);
                    lock.writeLock().lock();
                    try {
                        Path scopeDir = storageDir.resolve(scope.name().toLowerCase());
                        Files.createDirectories(scopeDir);

                        Path targetFile = scopeDir.resolve(key + JSON_SUFFIX);
                        Path tmpFile = scopeDir.resolve(key + TMP_SUFFIX);

                        Files.writeString(tmpFile, value);
                        io.kairo.core.util.SafeFileOps.atomicMove(tmpFile, targetFile);

                        log.debug("Saved raw entry {} to {}", key, targetFile);
                    } catch (IOException e) {
                        throw new MemoryStoreException("Failed to save raw entry: " + key, e);
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    /**
     * Load a raw string value by key and scope.
     *
     * @param key the storage key
     * @param scope the memory scope
     * @return a Mono emitting the value, or empty if not found
     */
    public Mono<String> loadRaw(String key, MemoryScope scope) {
        return Mono.fromCallable(
                        () -> {
                            validateKey(key);
                            lock.readLock().lock();
                            try {
                                Path file =
                                        storageDir
                                                .resolve(scope.name().toLowerCase())
                                                .resolve(key + JSON_SUFFIX);
                                if (Files.exists(file)) {
                                    return Files.readString(file);
                                }
                                return null;
                            } finally {
                                lock.readLock().unlock();
                            }
                        })
                .flatMap(val -> val != null ? Mono.just(val) : Mono.empty());
    }

    /**
     * Delete a raw entry by key and scope.
     *
     * @param key the storage key
     * @param scope the memory scope
     * @return a Mono completing when deleted
     */
    public Mono<Boolean> deleteRaw(String key, MemoryScope scope) {
        return Mono.fromCallable(
                () -> {
                    validateKey(key);
                    lock.writeLock().lock();
                    try {
                        Path file =
                                storageDir
                                        .resolve(scope.name().toLowerCase())
                                        .resolve(key + JSON_SUFFIX);
                        return Files.deleteIfExists(file);
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    /**
     * List all JSON file keys in a scope directory.
     *
     * @param scope the memory scope
     * @return a Flux of keys (filenames without .json extension)
     */
    public Flux<String> listKeys(MemoryScope scope) {
        return Mono.fromCallable(
                        () -> {
                            lock.readLock().lock();
                            try {
                                Path scopeDir = storageDir.resolve(scope.name().toLowerCase());
                                if (!Files.exists(scopeDir)) {
                                    return List.<String>of();
                                }
                                List<String> keys = new ArrayList<>();
                                try (DirectoryStream<Path> stream =
                                        Files.newDirectoryStream(scopeDir, "*" + JSON_SUFFIX)) {
                                    for (Path file : stream) {
                                        String filename = file.getFileName().toString();
                                        keys.add(
                                                filename.substring(
                                                        0,
                                                        filename.length() - JSON_SUFFIX.length()));
                                    }
                                }
                                return keys;
                            } finally {
                                lock.readLock().unlock();
                            }
                        })
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Validate that a storage key is safe for use as a filename.
     *
     * @param key the key to validate
     * @throws IllegalArgumentException if the key contains path traversal or invalid characters
     */
    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage key cannot be null or blank");
        }
        if (key.contains("..") || key.contains("/") || key.contains("\\")) {
            throw new IllegalArgumentException(
                    "Storage key contains path traversal characters: " + key);
        }
        if (!SAFE_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Storage key contains invalid characters (only alphanumeric, hyphen, "
                            + "underscore, dot allowed): "
                            + key);
        }
    }

    /**
     * Get the storage directory.
     *
     * @return the root storage path
     */
    public Path getStorageDir() {
        return storageDir;
    }
}
