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
package io.kairo.core.context;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.context.ContextBuilder;
import io.kairo.api.context.ContextBuilderConfig;
import io.kairo.api.context.ContextEntry;
import io.kairo.core.context.source.CustomContextSource;
import io.kairo.core.context.source.DateContextSource;
import io.kairo.core.context.source.SystemInfoContextSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultContextBuilderTest {

    @Test
    @DisplayName("Should collect from multiple sources ordered by priority")
    void testBasicAssembly() {
        ContextBuilder builder =
                new DefaultContextBuilder()
                        .addSource(new DateContextSource())
                        .addSource(new SystemInfoContextSource());

        List<ContextEntry> entries = builder.build();

        assertFalse(entries.isEmpty(), "Should have at least 2 entries");
        assertTrue(entries.size() >= 2, "Should have date + system info");

        // Date (priority 5) should come before SystemInfo (priority 10)
        assertEquals("date", entries.get(0).sourceName());
        assertEquals("system-info", entries.get(1).sourceName());
    }

    @Test
    @DisplayName("Should skip inactive sources")
    void testInactiveSource() {
        AtomicBoolean active = new AtomicBoolean(false);
        ContextBuilder builder =
                new DefaultContextBuilder()
                        .addSource(new DateContextSource())
                        .addSource(
                                CustomContextSource.of(
                                        "inactive", 15, () -> "hidden", active::get));

        List<ContextEntry> entries = builder.build();

        assertEquals(1, entries.size());
        assertEquals("date", entries.get(0).sourceName());
    }

    @Test
    @DisplayName("Should skip sources returning empty content")
    void testEmptySource() {
        ContextBuilder builder =
                new DefaultContextBuilder()
                        .addSource(new DateContextSource())
                        .addSource(CustomContextSource.of("empty", 15, () -> ""));

        List<ContextEntry> entries = builder.build();

        assertEquals(1, entries.size());
        assertEquals("date", entries.get(0).sourceName());
    }

    @Test
    @DisplayName("Should respect maxEntries config")
    void testMaxEntries() {
        ContextBuilderConfig config = ContextBuilderConfig.builder().maxEntries(1).build();
        ContextBuilder builder =
                new DefaultContextBuilder(config)
                        .addSource(new DateContextSource()) // priority 5
                        .addSource(new SystemInfoContextSource()); // priority 10

        List<ContextEntry> entries = builder.build();

        assertEquals(1, entries.size());
        assertEquals("date", entries.get(0).sourceName()); // highest priority kept
    }

    @Test
    @DisplayName("Should truncate entries exceeding maxEntryLength")
    void testMaxEntryLength() {
        ContextBuilderConfig config = ContextBuilderConfig.builder().maxEntryLength(10).build();
        ContextBuilder builder =
                new DefaultContextBuilder(config)
                        .addSource(CustomContextSource.of("long", 5, () -> "0123456789ABCDEF"));

        List<ContextEntry> entries = builder.build();

        assertEquals(1, entries.size());
        assertTrue(entries.get(0).content().contains("truncated"));
        // 10 chars of content + truncation marker should be less than original 16 chars + overhead
        assertTrue(entries.get(0).content().length() < 30);
    }

    @Test
    @DisplayName("Should remove source by name")
    void testRemoveSource() {
        ContextBuilder builder =
                new DefaultContextBuilder()
                        .addSource(new DateContextSource())
                        .addSource(new SystemInfoContextSource());

        builder.removeSource("date");
        List<ContextEntry> entries = builder.build();

        assertEquals(1, entries.size());
        assertEquals("system-info", entries.get(0).sourceName());
    }

    @Test
    @DisplayName("DateContextSource returns today's date")
    void testDateSource() {
        DateContextSource source = new DateContextSource();
        String content = source.collect();

        assertNotNull(content);
        assertTrue(content.startsWith("Current date:"));
        assertTrue(content.contains("20")); // Contains year
    }

    @Test
    @DisplayName("SystemInfoContextSource returns system details")
    void testSystemInfoSource() {
        SystemInfoContextSource source = new SystemInfoContextSource();
        String content = source.collect();

        assertNotNull(content);
        assertTrue(content.contains("System:"));
        assertTrue(content.contains("Java:"));
        assertTrue(content.contains("Working Directory:"));
    }

    @Test
    @DisplayName("CustomContextSource factory creates working source")
    void testCustomSource() {
        CustomContextSource source = CustomContextSource.of("test", 30, () -> "hello world");

        assertEquals("test", source.getName());
        assertEquals(30, source.priority());
        assertTrue(source.isActive());
        assertEquals("hello world", source.collect());
    }
}
