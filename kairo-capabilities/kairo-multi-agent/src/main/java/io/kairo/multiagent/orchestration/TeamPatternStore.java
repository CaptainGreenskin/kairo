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
package io.kairo.multiagent.orchestration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Persistent store for {@link TeamPattern}s — Level-2 team-collaboration self-evolution.
 *
 * <p>Storage: a single JSON array file {@code {baseDir}/patterns.json}. Team patterns are far fewer
 * than per-role lessons, so a single file is sufficient. Mirrors {@link ExpertMemoryStore}'s
 * file/reactive conventions.
 *
 * <p>{@link #recall} ranks past patterns by keyword overlap with the new goal (successful, higher
 * scoring, and more recent patterns first) so the planner can reuse compositions that worked for
 * similar tasks. Recall is synchronous (a fast single-file read) because it runs inside the
 * planner's already-blocking section; the write path is reactive/fire-and-forget.
 *
 * @since v0.11 (Experimental)
 */
public class TeamPatternStore {

    private static final Logger log = LoggerFactory.getLogger(TeamPatternStore.class);
    private static final TypeReference<List<TeamPattern>> LIST_TYPE = new TypeReference<>() {};
    private static final int MAX_PATTERNS = 500;

    private final Path file;
    private final ObjectMapper objectMapper;

    public TeamPatternStore(Path baseDir) {
        Objects.requireNonNull(baseDir, "baseDir must not be null");
        this.file = baseDir.resolve("patterns.json");
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** Default location: {@code ~/.kairo-code/team-patterns/}. */
    public TeamPatternStore() {
        this(Path.of(System.getProperty("user.home"), ".kairo-code", "team-patterns"));
    }

    /** Append a learned pattern (fire-and-forget friendly). Caps total at {@link #MAX_PATTERNS}. */
    public Mono<Void> record(TeamPattern pattern) {
        if (pattern == null) {
            return Mono.empty();
        }
        return Mono.<Void>fromCallable(
                        () -> {
                            Files.createDirectories(file.getParent());
                            List<TeamPattern> all = readAll();
                            all.add(pattern);
                            if (all.size() > MAX_PATTERNS) {
                                all =
                                        new ArrayList<>(
                                                all.subList(all.size() - MAX_PATTERNS, all.size()));
                            }
                            objectMapper.writeValue(file.toFile(), all);
                            log.debug(
                                    "Recorded team pattern: roles={} shape={} success={}",
                                    pattern.roleSequence(),
                                    pattern.dagShape(),
                                    pattern.success());
                            return null;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Recall the top-N patterns most relevant to {@code goal}, ranked by keyword overlap (then
     * success, score, recency). Synchronous fast read; returns empty on any error.
     */
    public List<TeamPattern> recall(String goal, int topN) {
        if (topN <= 0) {
            return List.of();
        }
        List<TeamPattern> all;
        try {
            all = readAll();
        } catch (RuntimeException ex) {
            log.debug("Team pattern recall failed: {}", ex.toString());
            return List.of();
        }
        if (all.isEmpty()) {
            return List.of();
        }
        Set<String> goalTokens = tokenize(goal);
        return all.stream()
                .sorted(
                        Comparator.<TeamPattern>comparingDouble(
                                        p -> overlap(goalTokens, tokenize(p.goal())))
                                .thenComparing(TeamPattern::success)
                                .thenComparingDouble(TeamPattern::score)
                                .thenComparing(TeamPattern::recordedAt)
                                .reversed())
                .limit(topN)
                .toList();
    }

    // ──── helpers ────

    private List<TeamPattern> readAll() {
        try {
            if (!Files.exists(file)) {
                return new ArrayList<>();
            }
            return new ArrayList<>(objectMapper.readValue(file.toFile(), LIST_TYPE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<String> tokenize(String s) {
        if (s == null || s.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(s.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() > 2)
                .collect(Collectors.toSet());
    }

    private static double overlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        long common = a.stream().filter(b::contains).count();
        return (double) common / Math.min(a.size(), b.size());
    }
}
