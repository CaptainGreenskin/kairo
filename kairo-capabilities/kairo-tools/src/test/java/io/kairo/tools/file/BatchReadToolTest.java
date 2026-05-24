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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchReadToolTest {

    private static final ToolContext CTX = new ToolContext("a", "s", Map.of());
    private final BatchReadTool tool = new BatchReadTool();

    @TempDir Path tempDir;

    @Test
    void readsSingleFile() throws IOException {
        Path f = tempDir.resolve("a.txt");
        Files.writeString(f, "hello world");

        ToolResult result = tool.execute(Map.of("paths", List.of(f.toString())), CTX).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("=== " + f + " ===");
        assertThat(result.content()).contains("hello world");
    }

    @Test
    void readsMultipleFiles() throws IOException {
        Path f1 = tempDir.resolve("one.txt");
        Path f2 = tempDir.resolve("two.txt");
        Files.writeString(f1, "content1");
        Files.writeString(f2, "content2");

        ToolResult result =
                tool.execute(Map.of("paths", List.of(f1.toString(), f2.toString())), CTX).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("content1");
        assertThat(result.content()).contains("content2");
        assertThat(result.metadata().get("successCount")).isEqualTo(2);
        assertThat(result.metadata().get("errorCount")).isEqualTo(0);
    }

    @Test
    void marksNonExistentFileAsError() throws IOException {
        Path exists = tempDir.resolve("real.txt");
        Files.writeString(exists, "real");
        String missing = tempDir.resolve("missing.txt").toString();

        ToolResult result =
                tool.execute(Map.of("paths", List.of(exists.toString(), missing)), CTX).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("[ERROR: file not found]");
        assertThat(result.content()).contains("real");
        assertThat(result.metadata().get("successCount")).isEqualTo(1);
        assertThat(result.metadata().get("errorCount")).isEqualTo(1);
    }

    @Test
    void truncatesLargeFilesAtMaxLines() throws IOException {
        Path f = tempDir.resolve("large.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            sb.append("line").append(i).append('\n');
        }
        Files.writeString(f, sb.toString());

        ToolResult result =
                tool.execute(Map.of("paths", List.of(f.toString()), "maxLinesPerFile", 10), CTX)
                        .block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("[... truncated at 10 lines");
        assertThat(result.content()).contains("line1");
        assertThat(result.content()).doesNotContain("line11");
    }

    @Test
    void rejectsMoreThan20Files() {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            paths.add("/some/path/file" + i + ".txt");
        }

        ToolResult result = tool.execute(Map.of("paths", paths), CTX).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Too many files");
    }

    @Test
    void rejectsEmptyPathsParam() {
        ToolResult result = tool.execute(Map.of(), CTX).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'paths'");
    }

    @Test
    void rejectsEmptyList() {
        ToolResult result = tool.execute(Map.of("paths", List.of()), CTX).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("non-empty");
    }

    @Test
    void marksDirectoryAsError() throws IOException {
        Path dir = tempDir.resolve("subdir");
        Files.createDirectory(dir);

        ToolResult result = tool.execute(Map.of("paths", List.of(dir.toString())), CTX).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("[ERROR: path is a directory]");
        assertThat(result.metadata().get("errorCount")).isEqualTo(1);
    }

    @Test
    void defaultMaxLinesIs500() throws IOException {
        Path f = tempDir.resolve("big.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 600; i++) {
            sb.append("L").append(i).append('\n');
        }
        Files.writeString(f, sb.toString());

        ToolResult result = tool.execute(Map.of("paths", List.of(f.toString())), CTX).block();

        assertThat(result.content()).contains("[... truncated at 500 lines");
    }

    @Test
    void readsExactly20FilesWithoutError() throws IOException {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Path f = tempDir.resolve("f" + i + ".txt");
            Files.writeString(f, "content" + i);
            paths.add(f.toString());
        }

        ToolResult result = tool.execute(Map.of("paths", paths), CTX).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata().get("successCount")).isEqualTo(20);
    }
}
