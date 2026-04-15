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
package io.kairo.core.model;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolDescriptionAdapterTest {

    private ToolDescriptionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ToolDescriptionAdapter();
    }

    private ToolDefinition tool(String name, String description) {
        return new ToolDefinition(name, description, ToolCategory.FILE_AND_CODE, null, null);
    }

    @Test
    @DisplayName("STANDARD verbosity returns tools unchanged")
    void standardVerbosityReturnsUnchanged() {
        List<ToolDefinition> tools = List.of(tool("read_file", "Read a file from disk."));
        List<ToolDefinition> result = adapter.adaptForModel(tools, ToolVerbosity.STANDARD);
        assertSame(tools, result);
    }

    @Test
    @DisplayName("CONCISE verbosity truncates description to first sentence")
    void conciseVerbosityTruncatesDescription() {
        ToolDefinition t =
                tool(
                        "read_file",
                        "Read a file from disk. Supports binary and text files. Very useful.");
        List<ToolDefinition> result = adapter.adaptForModel(List.of(t), ToolVerbosity.CONCISE);
        assertEquals("Read a file from disk.", result.get(0).description());
    }

    @Test
    @DisplayName("CONCISE truncates to 100 chars when no sentence boundary found")
    void conciseTruncatesToFirstSentence() {
        String longDesc = "A".repeat(150);
        ToolDefinition t = tool("some_tool", longDesc);
        List<ToolDefinition> result = adapter.adaptForModel(List.of(t), ToolVerbosity.CONCISE);
        assertEquals(103, result.get(0).description().length()); // 100 + "..."
        assertTrue(result.get(0).description().endsWith("..."));
    }

    @Test
    @DisplayName("VERBOSE verbosity adds usage hint")
    void verboseVerbosityAddsUsageHint() {
        ToolDefinition t = tool("read_file", "Read a file from disk.");
        List<ToolDefinition> result = adapter.adaptForModel(List.of(t), ToolVerbosity.VERBOSE);
        assertTrue(result.get(0).description().contains("Use this tool when you need to"));
        assertTrue(result.get(0).description().contains("read file contents"));
    }

    @Test
    @DisplayName("null tools list returns null")
    void nullToolsReturnsNull() {
        assertNull(adapter.adaptForModel(null, ToolVerbosity.CONCISE));
    }

    @Test
    @DisplayName("Empty tool list returns empty list")
    void emptyToolListReturnsEmpty() {
        List<ToolDefinition> result = adapter.adaptForModel(List.of(), ToolVerbosity.CONCISE);
        assertTrue(result.isEmpty());
    }
}
