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
package io.kairo.evolution.curator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kairo.api.evolution.SkillLifecycleState;
import io.kairo.api.evolution.SkillProvenance;
import io.kairo.api.evolution.SkillTelemetry;
import io.kairo.api.evolution.SkillTelemetryStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * File-backed {@link SkillTelemetryStore}. Persists all telemetry as a single JSON file (the
 * Hermes-style {@code .usage.json} sidecar) under {@code <dir>/.usage.json}.
 *
 * <p>Read/write cycles are serialized in-process by a {@link ReentrantLock}; writes are atomic via
 * tmp + {@link StandardCopyOption#ATOMIC_MOVE}. Across processes, callers should avoid two writers
 * for the same directory — for that we'd need an OS file lock; left out of this initial cut.
 */
public final class FileSkillTelemetryStore implements SkillTelemetryStore {

    private static final Logger log = LoggerFactory.getLogger(FileSkillTelemetryStore.class);
    private static final String FILE_NAME = ".usage.json";

    private final Path directory;
    private final Path file;
    private final ObjectMapper mapper;
    private final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<String, SkillTelemetry> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public FileSkillTelemetryStore(Path directory) {
        this.directory = directory;
        this.file = directory.resolve(FILE_NAME);
        this.mapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public Path file() {
        return file;
    }

    @Override
    public Mono<Optional<SkillTelemetry>> get(String skillName) {
        return Mono.fromCallable(
                        () -> {
                            ensureLoaded();
                            return Optional.ofNullable(cache.get(skillName));
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<SkillTelemetry> list() {
        return Mono.fromCallable(
                        () -> {
                            ensureLoaded();
                            return List.copyOf(cache.values());
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<SkillTelemetry> save(SkillTelemetry telemetry) {
        return Mono.fromCallable(
                        () -> {
                            ensureLoaded();
                            lock.lock();
                            try {
                                cache.put(telemetry.skillName(), telemetry);
                                persist();
                            } finally {
                                lock.unlock();
                            }
                            return telemetry;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(String skillName) {
        return Mono.<Void>fromRunnable(
                        () -> {
                            ensureLoaded();
                            lock.lock();
                            try {
                                if (cache.remove(skillName) != null) {
                                    persist();
                                }
                            } finally {
                                lock.unlock();
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<SkillTelemetry> upsert(
            String skillName,
            Instant at,
            UnaryOperator<SkillTelemetry> mutator,
            SkillProvenance seedProvenance) {
        return Mono.fromCallable(
                        () -> {
                            ensureLoaded();
                            lock.lock();
                            try {
                                SkillTelemetry base = cache.get(skillName);
                                if (base == null) {
                                    base = SkillTelemetry.initial(skillName, seedProvenance, at);
                                }
                                SkillTelemetry updated = mutator.apply(base);
                                cache.put(skillName, updated);
                                persist();
                                return updated;
                            } finally {
                                lock.unlock();
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ----- internals -----

    private void ensureLoaded() {
        if (loaded) return;
        lock.lock();
        try {
            if (loaded) return;
            if (Files.isRegularFile(file)) {
                try {
                    List<TelemetryDto> dtos =
                            mapper.readValue(
                                    Files.readAllBytes(file),
                                    new TypeReference<List<TelemetryDto>>() {});
                    for (TelemetryDto dto : dtos) {
                        cache.put(dto.skillName, dto.toRecord());
                    }
                } catch (IOException e) {
                    log.warn("Failed to load telemetry from {}: {}", file, e.getMessage());
                }
            }
            loaded = true;
        } finally {
            lock.unlock();
        }
    }

    private void persist() {
        try {
            Files.createDirectories(directory);
            List<TelemetryDto> dtos = new ArrayList<>(cache.size());
            for (SkillTelemetry t : cache.values()) {
                dtos.add(TelemetryDto.fromRecord(t));
            }
            dtos.sort((a, b) -> a.skillName.compareTo(b.skillName));
            Path tmp = Files.createTempFile(directory, ".usage_", ".tmp");
            try {
                byte[] body = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(dtos);
                Files.write(tmp, body);
                Files.move(
                        tmp,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist skill telemetry to " + file, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class TelemetryDto {
        public String skillName;
        public SkillLifecycleState state;
        public SkillProvenance provenance;
        public boolean pinned;
        public long useCount;
        public long viewCount;
        public long patchCount;
        public Instant lastUsedAt;
        public Instant lastViewedAt;
        public Instant lastPatchedAt;
        public Instant createdAt;
        public Instant archivedAt;
        public String absorbedInto;

        static TelemetryDto fromRecord(SkillTelemetry t) {
            TelemetryDto dto = new TelemetryDto();
            dto.skillName = t.skillName();
            dto.state = t.state();
            dto.provenance = t.provenance();
            dto.pinned = t.pinned();
            dto.useCount = t.useCount();
            dto.viewCount = t.viewCount();
            dto.patchCount = t.patchCount();
            dto.lastUsedAt = t.lastUsedAt();
            dto.lastViewedAt = t.lastViewedAt();
            dto.lastPatchedAt = t.lastPatchedAt();
            dto.createdAt = t.createdAt();
            dto.archivedAt = t.archivedAt();
            dto.absorbedInto = t.absorbedInto();
            return dto;
        }

        SkillTelemetry toRecord() {
            return new SkillTelemetry(
                    skillName,
                    state == null ? SkillLifecycleState.ACTIVE : state,
                    provenance == null ? SkillProvenance.AGENT_CREATED : provenance,
                    pinned,
                    useCount,
                    viewCount,
                    patchCount,
                    lastUsedAt,
                    lastViewedAt,
                    lastPatchedAt,
                    createdAt == null ? Instant.EPOCH : createdAt,
                    archivedAt,
                    absorbedInto);
        }
    }

    // Suppress IDE warning about unused — Jackson needs it for round-trips on extra props.
    @SuppressWarnings("unused")
    private static final Map<String, Object> EXTRA_PROPS_SLOT = new LinkedHashMap<>();
}
