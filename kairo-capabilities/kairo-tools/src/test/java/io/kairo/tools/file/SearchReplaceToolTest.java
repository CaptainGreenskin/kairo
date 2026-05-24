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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchReplaceToolTest {

    private static final ToolContext CTX = new ToolContext("a", "s", Map.of());

    private final SearchReplaceTool tool = new SearchReplaceTool();

    @TempDir Path tempDir;

    @Test
    void basicReplaceAll() throws IOException {
        Path f = writeFile("test.txt", "foo bar foo");

        ToolResult result = replace(f, "foo", "baz", null, null);

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(f)).isEqualTo("baz bar baz");
        assertThat(result.metadata().get("matchCount")).isEqualTo(2);
    }

    @Test
    void replaceFirstOnly() throws IOException {
        Path f = writeFile("test.txt", "foo foo foo");

        ToolResult result = replace(f, "foo", "X", false, null);

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(f)).isEqualTo("X foo foo");
        assertThat(result.metadata().get("matchCount")).isEqualTo(1);
    }

    @Test
    void captureGroupReference() throws IOException {
        Path f = writeFile("test.txt", "hello world");

        ToolResult result = replace(f, "(\\w+) (\\w+)", "$2 $1", null, null);

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(f)).isEqualTo("world hello");
    }

    @Test
    void caseInsensitiveFlag() throws IOException {
        Path f = writeFile("test.txt", "Foo FOO foo");

        ToolResult result = replace(f, "foo", "bar", null, "CASE_INSENSITIVE");

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(f)).isEqualTo("bar bar bar");
    }

    @Test
    void multilineFlag() throws IOException {
        Path f = writeFile("test.txt", "start\nmiddle\nend");

        ToolResult result = replace(f, "^middle$", "MIDDLE", null, "MULTILINE");

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(f)).isEqualTo("start\nMIDDLE\nend");
    }

    @Test
    void rejectsPatternLongerThan1000Chars() {
        String longPattern = "a".repeat(1001);

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "path", "/some/path",
                                        "search", longPattern,
                                        "replace", "x"),
                                CTX)
                        .block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Pattern too long");
    }

    @Test
    void rejectsInvalidRegex() throws IOException {
        Path f = writeFile("test.txt", "content");

        ToolResult result = replace(f, "[invalid", "x", null, null);

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Invalid regex pattern");
    }

    @Test
    void rejectsMissingFile() {
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "path", tempDir.resolve("nonexistent.txt").toString(),
                                        "search", "foo",
                                        "replace", "bar"),
                                CTX)
                        .block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("File not found");
    }

    @Test
    void rejectsMissingPath() {
        ToolResult result = tool.execute(Map.of("search", "foo", "replace", "bar"), CTX).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'path' is required");
    }

    @Test
    void rejectsMissingSearch() throws IOException {
        Path f = writeFile("test.txt", "content");

        ToolResult result =
                tool.execute(Map.of("path", f.toString(), "replace", "bar"), CTX).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'search' is required");
    }

    @Test
    void rejectsMissingReplace() throws IOException {
        Path f = writeFile("test.txt", "content");

        ToolResult result =
                tool.execute(Map.of("path", f.toString(), "search", "foo"), CTX).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'replace' is required");
    }

    @Test
    void noMatchProducesZeroCount() throws IOException {
        Path f = writeFile("test.txt", "hello world");

        ToolResult result = replace(f, "xyz", "abc", null, null);

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata().get("matchCount")).isEqualTo(0);
        assertThat(Files.readString(f)).isEqualTo("hello world");
    }

    @Test
    void rejectsDirectory() throws IOException {
        Path dir = tempDir.resolve("subdir");
        Files.createDirectory(dir);

        ToolResult result =
                tool.execute(Map.of("path", dir.toString(), "search", "foo", "replace", "bar"), CTX)
                        .block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("directory");
    }

    // ---- helpers ----

    private Path writeFile(String name, String content) throws IOException {
        Path f = tempDir.resolve(name);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    private ToolResult replace(
            Path file, String search, String replacement, Boolean replaceAll, String flags) {
        Map<String, Object> input =
                new java.util.HashMap<>(
                        Map.of("path", file.toString(), "search", search, "replace", replacement));
        if (replaceAll != null) input.put("replaceAll", replaceAll);
        if (flags != null) input.put("flags", flags);
        return tool.execute(input, CTX).block();
    }
}
