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

class EditToolTest {

    private EditTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new EditTool();
    }

    @Test
    void editWithExactMatch() throws IOException {
        Path file = tempDir.resolve("code.java");
        Files.writeString(file, "int x = 1;\nint y = 2;\nint z = 3;\n");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "path", file.toString(),
                                "originalText", "int y = 2;",
                                "newText", "int y = 42;"));
        assertFalse(result.isError());
        String content = Files.readString(file);
        assertTrue(content.contains("int y = 42;"));
        assertTrue(content.contains("int x = 1;"));
        assertTrue(content.contains("int z = 3;"));
    }

    @Test
    void editNonExistentFile() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "path", tempDir.resolve("missing.txt").toString(),
                                "originalText", "foo",
                                "newText", "bar"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("File not found"));
    }

    @Test
    void editWithNonMatchingSearchString() throws IOException {
        Path file = tempDir.resolve("stable.txt");
        Files.writeString(file, "keep this content");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "path", file.toString(),
                                "originalText", "not found text",
                                "newText", "replacement"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Could not find"));
    }

    @Test
    void editWithMultipleOccurrencesShouldFail() throws IOException {
        Path file = tempDir.resolve("dups.txt");
        Files.writeString(file, "hello\nhello\n");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "path", file.toString(),
                                "originalText", "hello",
                                "newText", "world"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("occurrences"));
    }

    @Test
    void editWithTrimmedWhitespaceFallback() throws IOException {
        Path file = tempDir.resolve("whitespace.txt");
        Files.writeString(file, "  hello world  \nother line\n");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "path", file.toString(),
                                "originalText", "  hello world  ",
                                "newText", "goodbye"));
        assertFalse(result.isError());
    }

    @Test
    void editMissingPathParameter() {
        ToolResult result = tool.execute(Map.of("originalText", "a", "newText", "b"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'path' is required"));
    }

    @Test
    void editMissingOriginalTextParameter() {
        Path file = tempDir.resolve("test.txt");
        ToolResult result = tool.execute(Map.of("path", file.toString(), "newText", "b"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'originalText' is required"));
    }

    @Test
    void editMissingNewTextParameter() {
        Path file = tempDir.resolve("test.txt");
        ToolResult result = tool.execute(Map.of("path", file.toString(), "originalText", "a"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'newText' is required"));
    }

    @Test
    void editMultilineContent() throws IOException {
        Path file = tempDir.resolve("multi.txt");
        Files.writeString(file, "function foo() {\n  return 1;\n}\n");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "path", file.toString(),
                                "originalText", "function foo() {\n  return 1;\n}",
                                "newText", "function foo() {\n  return 42;\n}"));
        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("return 42;"));
    }
}
