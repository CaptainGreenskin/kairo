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
package io.kairo.tools.file;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadToolTest {

    private ReadTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new ReadTool();
    }

    @Test
    void readExistingFile() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3\n");

        ToolResult result = tool.execute(Map.of("path", file.toString()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("line1"));
        assertTrue(result.content().contains("line2"));
        assertTrue(result.content().contains("line3"));
    }

    @Test
    void readNonExistentFile() {
        ToolResult result = tool.execute(Map.of("path", tempDir.resolve("missing.txt").toString()));
        assertTrue(result.isError());
        assertTrue(result.content().contains("File not found"));
    }

    @Test
    void readWithLineRange() throws IOException {
        Path file = tempDir.resolve("range.txt");
        Files.writeString(file, "a\nb\nc\nd\ne\n");

        ToolResult result =
                tool.execute(Map.of("path", file.toString(), "startLine", 2, "endLine", 4));
        assertFalse(result.isError());
        assertTrue(result.content().contains("b"));
        assertTrue(result.content().contains("c"));
        assertTrue(result.content().contains("d"));
        assertFalse(result.content().contains("     1"));
    }

    @Test
    void readWithStartLineOnly() throws IOException {
        Path file = tempDir.resolve("start.txt");
        Files.writeString(file, "a\nb\nc\n");

        ToolResult result = tool.execute(Map.of("path", file.toString(), "startLine", 2));
        assertFalse(result.isError());
        assertTrue(result.content().contains("b"));
        assertTrue(result.content().contains("c"));
    }

    @Test
    void readStartLineExceedsTotalLines() throws IOException {
        Path file = tempDir.resolve("short.txt");
        Files.writeString(file, "only\n");

        ToolResult result = tool.execute(Map.of("path", file.toString(), "startLine", 100));
        assertTrue(result.isError());
        assertTrue(result.content().contains("exceeds total lines"));
    }

    @Test
    void readDirectoryShouldFail() {
        ToolResult result = tool.execute(Map.of("path", tempDir.toString()));
        assertTrue(result.isError());
        assertTrue(result.content().contains("directory"));
    }

    @Test
    void readMissingPathParameter() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("'path' is required"));
    }

    @Test
    void readBlankPathParameter() {
        ToolResult result = tool.execute(Map.of("path", "  "));
        assertTrue(result.isError());
    }

    @Test
    void readLineNumbersAreFormatted() throws IOException {
        Path file = tempDir.resolve("numbered.txt");
        Files.writeString(file, "hello\nworld\n");

        ToolResult result = tool.execute(Map.of("path", file.toString()));
        assertFalse(result.isError());
        // Lines should have line numbers with │ separator
        assertTrue(result.content().contains("│"));
    }
}
