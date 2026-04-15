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
package io.kairo.api.context;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContextApiTest {

    // --- ContextEntry ---

    @Test
    void contextEntryFields() {
        ContextEntry entry = new ContextEntry("git-status", 15, "M file.java");
        assertEquals("git-status", entry.sourceName());
        assertEquals(15, entry.priority());
        assertEquals("M file.java", entry.content());
    }

    @Test
    void contextEntryNullSourceNameThrows() {
        assertThrows(NullPointerException.class, () -> new ContextEntry(null, 0, "content"));
    }

    @Test
    void contextEntryNullContentThrows() {
        assertThrows(NullPointerException.class, () -> new ContextEntry("source", 0, null));
    }

    // --- TokenBudget ---

    @Test
    void tokenBudgetFields() {
        TokenBudget budget = new TokenBudget(200_000, 150_000, 50_000, 0.75f, 4096);
        assertEquals(200_000, budget.total());
        assertEquals(150_000, budget.used());
        assertEquals(50_000, budget.remaining());
        assertEquals(0.75f, budget.pressure(), 0.001);
        assertEquals(4096, budget.reservedForResponse());
    }

    // --- BoundaryMarker ---

    @Test
    void boundaryMarkerFields() {
        Instant now = Instant.now();
        BoundaryMarker marker = new BoundaryMarker(now, "truncation", 50, 20, 5000);
        assertEquals(now, marker.timestamp());
        assertEquals("truncation", marker.strategyName());
        assertEquals(50, marker.originalMessageCount());
        assertEquals(20, marker.compactedMessageCount());
        assertEquals(5000, marker.tokensSaved());
    }

    // --- ContextState ---

    @Test
    void contextStateFullConstructor() {
        ContextState state = new ContextState(200_000, 150_000, 0.75f, 30, 200_000);
        assertEquals(200_000, state.totalTokens());
        assertEquals(150_000, state.usedTokens());
        assertEquals(0.75f, state.pressure(), 0.001);
        assertEquals(30, state.messageCount());
        assertEquals(200_000, state.contextWindow());
    }

    @Test
    void contextStateBackwardCompatConstructor() {
        ContextState state = new ContextState(100_000, 80_000, 0.8f, 20);
        assertEquals(0, state.contextWindow());
        assertEquals(100_000, state.totalTokens());
    }

    // --- ContextBuilderConfig ---

    @Test
    void contextBuilderConfigDefaults() {
        ContextBuilderConfig config = ContextBuilderConfig.defaults();
        assertEquals("\n\n", config.sectionDelimiter());
        assertEquals(0, config.maxEntries());
        assertEquals(0, config.maxEntryLength());
    }

    @Test
    void contextBuilderConfigBuilder() {
        ContextBuilderConfig config =
                ContextBuilderConfig.builder()
                        .sectionDelimiter("\n---\n")
                        .maxEntries(10)
                        .maxEntryLength(5000)
                        .build();

        assertEquals("\n---\n", config.sectionDelimiter());
        assertEquals(10, config.maxEntries());
        assertEquals(5000, config.maxEntryLength());
    }

    // --- ContextSource.of ---

    @Test
    void contextSourceOfFactory() {
        ContextSource source = ContextSource.of("test-source", 5, () -> "hello world");

        assertEquals("test-source", source.getName());
        assertEquals(5, source.priority());
        assertTrue(source.isActive());
        assertEquals("hello world", source.collect());
        assertEquals("ContextSource[test-source]", source.toString());
    }

    // --- CompactionConfig ---

    @Test
    void compactionConfigBackwardCompat() {
        CompactionConfig config = new CompactionConfig(50_000, true, null);
        assertEquals(50_000, config.targetTokens());
        assertTrue(config.preserveVerbatim());
        assertNull(config.modelProvider());
        assertEquals(CompactionConfig.PartialDirection.FROM, config.partialDirection());
        assertNull(config.boundaryMarkerId());
    }

    @Test
    void compactionConfigFullConstructor() {
        CompactionConfig config =
                new CompactionConfig(
                        30_000, false, null, CompactionConfig.PartialDirection.UP_TO, "marker-1");
        assertEquals(30_000, config.targetTokens());
        assertFalse(config.preserveVerbatim());
        assertEquals(CompactionConfig.PartialDirection.UP_TO, config.partialDirection());
        assertEquals("marker-1", config.boundaryMarkerId());
    }

    @Test
    void compactionConfigPartialDirectionValues() {
        CompactionConfig.PartialDirection[] values = CompactionConfig.PartialDirection.values();
        assertEquals(2, values.length);
        assertNotNull(CompactionConfig.PartialDirection.valueOf("FROM"));
        assertNotNull(CompactionConfig.PartialDirection.valueOf("UP_TO"));
    }
}
