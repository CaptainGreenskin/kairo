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
import io.kairo.api.workspace.Workspace;
import io.kairo.api.workspace.WorkspaceRequest;
import io.kairo.core.workspace.LocalDirectoryWorkspaceProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchWriteToolTest {

    private final BatchWriteTool tool = new BatchWriteTool();

    @TempDir Path tempDir;

    private ToolContext ctx(Path root) {
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(root).acquire(WorkspaceRequest.writable(null));
        return new ToolContext("a", "s", Map.of(), null, null, ws);
    }

    private Map<String, Object> fileEntry(String path, String content) {
        Map<String, Object> m = new HashMap<>();
        m.put("path", path);
        m.put("content", content);
        return m;
    }

    @Test
    void writesSingleFile() throws IOException {
        List<Object> files = List.of(fileEntry("single.txt", "hello"));
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("=== single.txt ===");
        assertThat(result.content()).contains("Successfully written");
        assertThat(Files.readString(tempDir.resolve("single.txt"))).isEqualTo("hello");
    }

    @Test
    void writesMultipleFiles() throws IOException {
        List<Object> files =
                List.of(fileEntry("a.txt", "contentA"), fileEntry("b.txt", "contentB"));
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(tempDir.resolve("a.txt"))).isEqualTo("contentA");
        assertThat(Files.readString(tempDir.resolve("b.txt"))).isEqualTo("contentB");
        assertThat(result.metadata().get("successCount")).isEqualTo(2);
        assertThat(result.metadata().get("errorCount")).isEqualTo(0);
    }

    @Test
    void createsParentDirectories() throws IOException {
        List<Object> files = List.of(fileEntry("deep/nested/dir/file.txt", "nested"));
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(tempDir.resolve("deep/nested/dir/file.txt")))
                .isEqualTo("nested");
    }

    @Test
    void rejectsMoreThan50Files() {
        List<Object> files = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            files.add(fileEntry("f" + i + ".txt", "x"));
        }

        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Too many files");
    }

    @Test
    void emptyPathFailsAllFilesAtomic() throws IOException {
        List<Object> files =
                List.of(
                        fileEntry("good.txt", "ok"),
                        fileEntry("", "content"),
                        fileEntry("also_good.txt", "ok2"));

        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        // Atomic behavior: all entries fail when any validation error exists
        assertThat(result.metadata().get("successCount")).isEqualTo(0);
        assertThat(result.metadata().get("errorCount")).isEqualTo(3);
        assertThat(result.content()).contains("[ERROR: empty path]");
        assertThat(result.content()).contains("skipped due to validation errors");
        // No files should be written
        assertThat(Files.exists(tempDir.resolve("good.txt"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("also_good.txt"))).isFalse();
    }

    @Test
    void emptyContentWritesEmptyFile() throws IOException {
        List<Object> files = List.of(fileEntry("empty.txt", ""));
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(tempDir.resolve("empty.txt"))).isEmpty();
        assertThat(result.metadata().get("successCount")).isEqualTo(1);
    }

    @Test
    void pathTraversalIsRejected() throws IOException {
        List<Object> files =
                List.of(fileEntry("../escape.txt", "evil"), fileEntry("safe.txt", "ok"));

        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        // Atomic behavior: all entries fail when any validation error exists
        assertThat(result.metadata().get("successCount")).isEqualTo(0);
        assertThat(result.metadata().get("errorCount")).isEqualTo(2);
        assertThat(result.content()).contains("[ERROR: path traversal");
        assertThat(result.content()).contains("skipped due to validation errors");
        assertThat(Files.exists(tempDir.getParent().resolve("escape.txt"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("safe.txt"))).isFalse();
    }

    @Test
    void metadataSuccessCountIsCorrect() throws IOException {
        List<Object> files =
                List.of(
                        fileEntry("one.txt", "a"),
                        fileEntry("two.txt", "b"),
                        fileEntry("three.txt", "c"));

        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata().get("successCount")).isEqualTo(3);
        assertThat(result.metadata().get("errorCount")).isEqualTo(0);
    }

    @Test
    void relativePathResolvesAgainstWorkspaceRoot() throws IOException {
        List<Object> files = List.of(fileEntry("relative.txt", "rooted"));
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(tempDir.resolve("relative.txt"))).isEqualTo("rooted");
    }

    @Test
    void missingFilesParamReturnsError() {
        ToolResult result = tool.execute(Map.of(), ctx(tempDir)).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'files'");
    }

    @Test
    void emptyFilesArrayReturnsError() {
        ToolResult result = tool.execute(Map.of("files", List.of()), ctx(tempDir)).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("non-empty");
    }

    @Test
    void createsDirsFalseFailsIfParentMissing() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("path", "missing/sub/file.txt");
        entry.put("content", "x");
        entry.put("createDirs", "false");

        ToolResult result = tool.execute(Map.of("files", List.of(entry)), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata().get("errorCount")).isEqualTo(1);
        assertThat(result.content()).contains("[ERROR:");
    }

    @Test
    void perFileResultWithValidationError() throws IOException {
        List<Object> files = List.of(fileEntry("ok.txt", "yes"), fileEntry("", "bad"));

        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fileResults =
                (List<Map<String, Object>>) result.metadata().get("files");
        assertThat(fileResults).hasSize(2);

        // Atomic behavior: all entries fail
        Map<String, Object> ok = fileResults.get(0);
        assertThat(ok.get("path")).isEqualTo("ok.txt");
        assertThat(ok.get("success")).isEqualTo(false);
        assertThat(ok.get("error")).isEqualTo("skipped due to validation errors");

        Map<String, Object> bad = fileResults.get(1);
        assertThat(bad.get("path")).isEqualTo("");
        assertThat(bad.get("success")).isEqualTo(false);
        assertThat(bad.get("error")).isEqualTo("empty path");
    }

    @Test
    void missingContentFailsAllFilesAtomic() throws IOException {
        Map<String, Object> noContent = new HashMap<>();
        noContent.put("path", "noContent.txt");
        // no 'content' key

        List<Object> files = List.of(fileEntry("good.txt", "ok"), noContent);

        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        // Atomic behavior: all entries fail when any validation error exists
        assertThat(result.metadata().get("successCount")).isEqualTo(0);
        assertThat(result.metadata().get("errorCount")).isEqualTo(2);
        assertThat(result.content()).contains("[ERROR: missing required field 'content']");
        assertThat(result.content()).contains("skipped due to validation errors");
        // No files should be written
        assertThat(Files.exists(tempDir.resolve("good.txt"))).isFalse();
    }

    @Test
    void writesExactly50FilesWithoutError() throws IOException {
        List<Object> files = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            files.add(fileEntry("f" + i + ".txt", "content" + i));
        }

        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata().get("successCount")).isEqualTo(50);
        assertThat(result.metadata().get("errorCount")).isEqualTo(0);
    }

    @Test
    void dryRunValidatesPathsButDoesNotWrite() throws IOException {
        List<Object> files = List.of(fileEntry("dryrun.txt", "should not exist"));

        ToolResult result =
                tool.execute(Map.of("files", files, "dryRun", true), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("dry run");
        assertThat(result.metadata().get("dryRun")).isEqualTo(true);
        assertThat(result.metadata().get("successCount")).isEqualTo(1);
        assertThat(Files.exists(tempDir.resolve("dryrun.txt"))).isFalse();
    }

    @Test
    void dryRunWithMixedValidAndInvalidPaths() throws IOException {
        List<Object> files =
                List.of(
                        fileEntry("valid.txt", "ok"),
                        fileEntry("../escape.txt", "evil"),
                        fileEntry("also_valid.txt", "ok2"));

        ToolResult result =
                tool.execute(Map.of("files", files, "dryRun", true), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata().get("dryRun")).isEqualTo(true);
        // Atomic behavior: all entries fail when any validation error exists
        assertThat(result.metadata().get("successCount")).isEqualTo(0);
        assertThat(result.metadata().get("errorCount")).isEqualTo(3);
        assertThat(result.content()).contains("[ERROR: path traversal");
        assertThat(result.content()).contains("skipped due to validation errors");
        assertThat(Files.exists(tempDir.resolve("valid.txt"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("also_valid.txt"))).isFalse();
    }

    @Test
    void twoPhaseExecutionValidatesAllBeforeWriting() throws IOException {
        List<Object> files =
                List.of(
                        fileEntry("first.txt", "content1"),
                        fileEntry("", "bad entry"),
                        fileEntry("second.txt", "content2"));

        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        // Atomic behavior: all entries fail when any validation error exists
        assertThat(result.metadata().get("successCount")).isEqualTo(0);
        assertThat(result.metadata().get("errorCount")).isEqualTo(3);
        // In two-phase execution, if validation fails, no files are written
        assertThat(Files.exists(tempDir.resolve("first.txt"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("second.txt"))).isFalse();
    }

    @Test
    void rollbackOnWriteFailure() throws IOException {
        // Create a file that we'll make fail by writing to a read-only directory
        Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectories(readOnlyDir);
        Path readOnlyFile = readOnlyDir.resolve("file.txt");
        Files.writeString(readOnlyFile, "initial");
        readOnlyFile.toFile().setWritable(false);

        List<Object> files =
                List.of(
                        fileEntry("success1.txt", "will be rolled back"),
                        fileEntry("readonly/file.txt", "will fail"),
                        fileEntry("success2.txt", "will also be rolled back"));

        try {
            ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

            assertThat(result.isError()).isFalse();
            assertThat(result.metadata().get("rolledBack")).isEqualTo(true);
            assertThat(result.metadata().get("successCount")).isEqualTo(0);
            assertThat(result.content()).contains("rolled back");

            // Verify rollback: first file should not exist
            assertThat(Files.exists(tempDir.resolve("success1.txt"))).isFalse();
            assertThat(Files.exists(tempDir.resolve("success2.txt"))).isFalse();
        } finally {
            // Restore write permission for cleanup
            readOnlyFile.toFile().setWritable(true);
        }
    }

    @Test
    void dryRunMetadataContainsDryRunFlag() throws IOException {
        List<Object> files = List.of(fileEntry("test.txt", "content"));

        ToolResult result =
                tool.execute(Map.of("files", files, "dryRun", true), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata()).containsKey("dryRun");
        assertThat(result.metadata().get("dryRun")).isEqualTo(true);
    }

    @Test
    void normalWriteMetadataContainsDryRunFlagFalse() throws IOException {
        List<Object> files = List.of(fileEntry("test.txt", "content"));

        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir)).block();

        assertThat(result.isError()).isFalse();
        assertThat(result.metadata()).containsKey("dryRun");
        assertThat(result.metadata().get("dryRun")).isEqualTo(false);
    }
}
