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
package io.kairo.evolution;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTrustLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * File-based implementation of {@link EvolvedSkillStore}. Persists each evolved skill as a
 * properties file in a configurable directory ({@code .kairo/evolved-skills/} by default).
 *
 * <p>Thread-safe via ReadWriteLock. File writes are atomic (write to .tmp, then move).
 */
public final class FileEvolvedSkillStore implements EvolvedSkillStore {

    private static final Logger log = LoggerFactory.getLogger(FileEvolvedSkillStore.class);
    private static final String FILE_SUFFIX = ".skill";
    private static final String SEPARATOR = "---";

    private final Path directory;
    private final ConcurrentHashMap<String, EvolvedSkill> cache = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean loaded = false;

    public FileEvolvedSkillStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public Mono<EvolvedSkill> save(EvolvedSkill skill) {
        return Mono.fromCallable(
                        () -> {
                            ensureLoaded();
                            lock.writeLock().lock();
                            try {
                                writeSkillFile(skill);
                                cache.put(skill.name(), skill);
                                return skill;
                            } finally {
                                lock.writeLock().unlock();
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Optional<EvolvedSkill>> get(String name) {
        return Mono.fromCallable(
                        () -> {
                            ensureLoaded();
                            lock.readLock().lock();
                            try {
                                return Optional.ofNullable(cache.get(name));
                            } finally {
                                lock.readLock().unlock();
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<EvolvedSkill> list() {
        return Mono.fromCallable(
                        () -> {
                            ensureLoaded();
                            lock.readLock().lock();
                            try {
                                return cache.values().stream().toList();
                            } finally {
                                lock.readLock().unlock();
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Void> delete(String name) {
        return Mono.<Void>fromRunnable(
                        () -> {
                            ensureLoaded();
                            lock.writeLock().lock();
                            try {
                                Path file = directory.resolve(sanitizeName(name) + FILE_SUFFIX);
                                Files.deleteIfExists(file);
                                cache.remove(name);
                            } catch (IOException e) {
                                throw new RuntimeException(
                                        "Failed to delete skill file: " + name, e);
                            } finally {
                                lock.writeLock().unlock();
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Path directory() {
        return directory;
    }

    private void ensureLoaded() {
        if (loaded) return;
        lock.writeLock().lock();
        try {
            if (loaded) return;
            loadFromDisk();
            loaded = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void loadFromDisk() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.toString().endsWith(FILE_SUFFIX))
                    .forEach(
                            p -> {
                                try {
                                    String content = Files.readString(p);
                                    EvolvedSkill skill = deserialize(content);
                                    cache.put(skill.name(), skill);
                                } catch (Exception e) {
                                    log.warn("Failed to load skill from {}: {}", p, e.getMessage());
                                }
                            });
        } catch (IOException e) {
            log.warn("Failed to list skill directory {}: {}", directory, e.getMessage());
        }
    }

    private void writeSkillFile(EvolvedSkill skill) throws IOException {
        Files.createDirectories(directory);
        String fileName = sanitizeName(skill.name()) + FILE_SUFFIX;
        Path target = directory.resolve(fileName);
        Path tmp = directory.resolve(fileName + ".tmp");

        String content = serialize(skill);
        Files.writeString(tmp, content);
        Files.move(
                tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    static String serialize(EvolvedSkill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(skill.name()).append('\n');
        sb.append("version=").append(skill.version()).append('\n');
        sb.append("description=").append(escape(skill.description())).append('\n');
        sb.append("category=").append(escape(skill.category())).append('\n');
        sb.append("tags=").append(String.join(",", skill.tags())).append('\n');
        sb.append("trustLevel=").append(skill.trustLevel().name()).append('\n');
        sb.append("createdAt=").append(skill.createdAt()).append('\n');
        sb.append("updatedAt=").append(skill.updatedAt()).append('\n');
        sb.append("usageCount=").append(skill.usageCount()).append('\n');
        if (skill.metadata() != null) {
            for (Map.Entry<String, String> e : skill.metadata().entrySet()) {
                sb.append("meta.")
                        .append(e.getKey())
                        .append('=')
                        .append(escape(e.getValue()))
                        .append('\n');
            }
        }
        sb.append(SEPARATOR).append('\n');
        sb.append(skill.instructions());
        return sb.toString();
    }

    static EvolvedSkill deserialize(String content) {
        int sepIdx = content.indexOf(SEPARATOR + "\n");
        if (sepIdx < 0) {
            throw new IllegalArgumentException("Invalid skill file: no separator found");
        }

        String header = content.substring(0, sepIdx);
        String instructions = content.substring(sepIdx + SEPARATOR.length() + 1);

        Map<String, String> props = new LinkedHashMap<>();
        for (String line : header.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                props.put(line.substring(0, eq), unescape(line.substring(eq + 1)));
            }
        }

        Set<String> tags = new LinkedHashSet<>();
        String tagsStr = props.getOrDefault("tags", "");
        if (!tagsStr.isBlank()) {
            for (String tag : tagsStr.split(",")) {
                tags.add(tag.trim());
            }
        }

        Map<String, String> metadata = new HashMap<>();
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (e.getKey().startsWith("meta.")) {
                metadata.put(e.getKey().substring(5), e.getValue());
            }
        }

        return new EvolvedSkill(
                props.getOrDefault("name", "unknown"),
                props.getOrDefault("version", "1.0"),
                props.getOrDefault("description", ""),
                instructions,
                props.getOrDefault("category", "general"),
                tags,
                SkillTrustLevel.valueOf(props.getOrDefault("trustLevel", "DRAFT")),
                metadata.isEmpty() ? null : metadata,
                Instant.parse(props.getOrDefault("createdAt", Instant.EPOCH.toString())),
                Instant.parse(props.getOrDefault("updatedAt", Instant.EPOCH.toString())),
                Long.parseLong(props.getOrDefault("usageCount", "0")));
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        if (value == null) return "";
        return value.replace("\\n", "\n").replace("\\\\", "\\");
    }
}
