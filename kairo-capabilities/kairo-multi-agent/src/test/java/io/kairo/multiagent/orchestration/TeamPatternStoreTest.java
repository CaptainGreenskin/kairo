/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.multiagent.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link TeamPatternStore} (Level-2 team self-evolution storage + recall). */
class TeamPatternStoreTest {

    @Test
    @DisplayName("record then recall returns the persisted pattern")
    void recordAndRecall(@TempDir Path dir) {
        TeamPatternStore store = new TeamPatternStore(dir);
        store.record(
                        new TeamPattern(
                                "implement a REST API for users",
                                List.of("expert:researcher", "expert:coder", "expert:reviewer"),
                                "serial",
                                true,
                                1.0,
                                Instant.now()))
                .block();

        List<TeamPattern> recalled = store.recall("build a REST API", 5);
        assertEquals(1, recalled.size());
        assertEquals(
                List.of("expert:researcher", "expert:coder", "expert:reviewer"),
                recalled.get(0).roleSequence());
    }

    @Test
    @DisplayName("recall ranks keyword-relevant patterns above unrelated ones")
    void recallRanksByRelevance(@TempDir Path dir) {
        TeamPatternStore store = new TeamPatternStore(dir);
        store.record(
                        new TeamPattern(
                                "write database migration scripts",
                                List.of("expert:coder"),
                                "serial",
                                true,
                                1.0,
                                Instant.now()))
                .block();
        store.record(
                        new TeamPattern(
                                "design a REST API for the users service",
                                List.of("expert:architect", "expert:coder"),
                                "serial",
                                true,
                                1.0,
                                Instant.now()))
                .block();

        List<TeamPattern> recalled = store.recall("create a REST API users endpoint", 1);
        assertEquals(1, recalled.size());
        assertTrue(
                recalled.get(0).roleSequence().contains("expert:architect"),
                "expected the API-related pattern to rank first");
    }

    @Test
    @DisplayName("empty store recalls nothing; topN<=0 returns empty")
    void emptyAndGuard(@TempDir Path dir) {
        TeamPatternStore store = new TeamPatternStore(dir);
        assertTrue(store.recall("anything", 5).isEmpty());
        store.record(
                        new TeamPattern(
                                "x", List.of("expert:coder"), "serial", true, 1.0, Instant.now()))
                .block();
        assertTrue(store.recall("x", 0).isEmpty());
        assertFalse(store.recall("x", 5).isEmpty());
    }
}
