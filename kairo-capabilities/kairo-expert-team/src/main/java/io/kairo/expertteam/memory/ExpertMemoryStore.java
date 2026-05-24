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
package io.kairo.expertteam.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Persistent store for expert lessons learned, enabling cross-task knowledge transfer.
 *
 * <p>Storage layout: {@code {baseDir}/{roleId}/{namespace}.json} — each file contains a JSON array
 * of {@link ExpertMemoryEntry} objects.
 *
 * <p><strong>Extraction model:</strong> Memory extraction is BATCH — a single call at team
 * completion, not per-step. During execution, raw step outcomes are cached in-memory; after
 * TEAM_COMPLETED, a batch call extracts lessons from all steps for each role. This avoids N extra
 * LLM calls for N-step DAGs.
 *
 * <p><strong>Thread safety:</strong> All reactive operations subscribe on {@link
 * Schedulers#boundedElastic()} for blocking file I/O. Writes to the same file are serialized via
 * the reactive chain; concurrent writes to <em>different</em> namespaces are inherently safe (they
 * target different files).
 *
 * @since v0.10 (Experimental)
 */
public class ExpertMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(ExpertMemoryStore.class);
    private static final TypeReference<List<ExpertMemoryEntry>> LIST_TYPE =
            new TypeReference<>() {};

    private final Path baseDir;
    private final ObjectMapper objectMapper;

    /**
     * Creates a store at the given base directory.
     *
     * @param baseDir root directory for expert memory files
     */
    public ExpertMemoryStore(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir must not be null");
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** Creates a store at the default location: {@code ~/.kairo-code/expert-memory/}. */
    public ExpertMemoryStore() {
        this(Path.of(System.getProperty("user.home"), ".kairo-code", "expert-memory"));
    }

    /**
     * Records batch lessons for a role after team completion.
     *
     * <p>Called once per role at team end with ALL that role's extracted lessons. The caller (e.g.
     * a synthesizer step) is responsible for LLM extraction — this method only persists the
     * results.
     *
     * @param roleId the expert role identifier
     * @param namespace logical grouping key
     * @param lessons the extracted lessons to persist
     * @return a Mono completing when persistence is done
     */
    public Mono<Void> recordLessons(
            String roleId, String namespace, List<ExpertMemoryEntry> lessons) {
        if (roleId == null || roleId.isBlank()) {
            return Mono.error(new IllegalArgumentException("roleId must not be null or blank"));
        }
        if (namespace == null || namespace.isBlank()) {
            namespace = roleId;
        }
        if (lessons == null || lessons.isEmpty()) {
            return Mono.empty();
        }

        final String ns = namespace;
        return Mono.<Void>fromCallable(
                        () -> {
                            Path file = resolveFile(roleId, ns);
                            Files.createDirectories(file.getParent());

                            List<ExpertMemoryEntry> existing = readEntries(file);
                            existing.addAll(lessons);
                            objectMapper.writeValue(file.toFile(), existing);
                            log.debug(
                                    "Recorded {} lessons for role={} namespace={}",
                                    lessons.size(),
                                    roleId,
                                    ns);
                            return null;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Recalls the top-N most relevant memories for a role in a namespace, ordered by relevance
     * score descending.
     *
     * @param roleId the expert role identifier
     * @param namespace logical grouping key
     * @param topN maximum number of entries to return
     * @return a Flux of entries sorted by relevanceScore descending
     */
    public Flux<ExpertMemoryEntry> recall(String roleId, String namespace, int topN) {
        if (roleId == null || roleId.isBlank()) {
            return Flux.error(new IllegalArgumentException("roleId must not be null or blank"));
        }
        if (namespace == null || namespace.isBlank()) {
            namespace = roleId;
        }
        if (topN <= 0) {
            return Flux.empty();
        }

        final String ns = namespace;
        return Mono.fromCallable(
                        () -> {
                            Path file = resolveFile(roleId, ns);
                            List<ExpertMemoryEntry> entries = readEntries(file);
                            entries.sort(
                                    Comparator.comparingDouble(ExpertMemoryEntry::relevanceScore)
                                            .reversed());
                            return entries.stream().limit(topN).toList();
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Clears all memories for a namespace across all roles.
     *
     * @param namespace the namespace to clear
     * @return a Mono completing when all matching files are deleted
     */
    public Mono<Void> clear(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return Mono.error(new IllegalArgumentException("namespace must not be null or blank"));
        }

        return Mono.<Void>fromCallable(
                        () -> {
                            if (!Files.isDirectory(baseDir)) {
                                return null;
                            }
                            String filename = namespace + ".json";
                            try (DirectoryStream<Path> roleDirs =
                                    Files.newDirectoryStream(baseDir)) {
                                for (Path roleDir : roleDirs) {
                                    if (Files.isDirectory(roleDir)) {
                                        Path target = roleDir.resolve(filename);
                                        if (Files.exists(target)) {
                                            Files.delete(target);
                                            log.debug("Cleared memory file: {}", target);
                                        }
                                    }
                                }
                            }
                            return null;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Lists all available namespaces for a role.
     *
     * @param roleId the expert role identifier
     * @return a Flux of namespace names
     */
    public Flux<String> listNamespaces(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return Flux.error(new IllegalArgumentException("roleId must not be null or blank"));
        }

        return Mono.fromCallable(
                        () -> {
                            Path roleDir = baseDir.resolve(sanitize(roleId));
                            if (!Files.isDirectory(roleDir)) {
                                return List.<String>of();
                            }
                            List<String> namespaces = new ArrayList<>();
                            try (DirectoryStream<Path> files =
                                    Files.newDirectoryStream(roleDir, "*.json")) {
                                for (Path file : files) {
                                    String name = file.getFileName().toString();
                                    namespaces.add(
                                            name.substring(0, name.length() - ".json".length()));
                                }
                            }
                            return namespaces;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    // ──── Internal helpers ────

    private Path resolveFile(String roleId, String namespace) {
        return baseDir.resolve(sanitize(roleId)).resolve(sanitize(namespace) + ".json");
    }

    private List<ExpertMemoryEntry> readEntries(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(objectMapper.readValue(file.toFile(), LIST_TYPE));
    }

    /**
     * Sanitizes a name for use as a filesystem path component. Replaces path-unsafe characters with
     * underscores.
     */
    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
