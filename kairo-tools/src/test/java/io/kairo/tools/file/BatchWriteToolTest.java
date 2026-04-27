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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchWriteToolTest {

    private BatchWriteTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new BatchWriteTool();
    }

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
    void writesMultipleFiles() throws IOException {
        List<Object> files = List.of(fileEntry("a.txt", "hello"), fileEntry("b.txt", "world"));
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir));
        assertFalse(result.isError(), result.content());
        assertEquals("hello", Files.readString(tempDir.resolve("a.txt")));
        assertEquals("world", Files.readString(tempDir.resolve("b.txt")));
        assertEquals(2, result.metadata().get("filesWritten"));
    }

    @Test
    void dryRunValidatesWithoutWriting() {
        List<Object> files = List.of(fileEntry("dryrun.txt", "content"));
        ToolResult result = tool.execute(Map.of("files", files, "dryRun", "true"), ctx(tempDir));
        assertFalse(result.isError(), result.content());
        assertFalse(
                Files.exists(tempDir.resolve("dryrun.txt")),
                "File should not be written in dry run");
        assertEquals(true, result.metadata().get("dryRun"));
        assertEquals(1, result.metadata().get("filesValidated"));
    }

    @Test
    void createsDirsAutomatically() throws IOException {
        List<Object> files = List.of(fileEntry("deep/nested/file.txt", "nested"));
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir));
        assertFalse(result.isError(), result.content());
        assertEquals("nested", Files.readString(tempDir.resolve("deep/nested/file.txt")));
    }

    @Test
    void createDirsFalseFailsIfParentMissing() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("path", "missing/dir/file.txt");
        entry.put("content", "x");
        entry.put("createDirs", "false");
        ToolResult result = tool.execute(Map.of("files", List.of(entry)), ctx(tempDir));
        assertTrue(result.isError(), result.content());
        assertTrue(
                result.content().contains("rolled back") || result.content().contains("failed"),
                result.content());
    }

    @Test
    void pathTraversalIsRejected() {
        List<Object> files = List.of(fileEntry("../escape.txt", "evil"));
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir));
        assertTrue(result.isError(), result.content());
        assertTrue(result.content().contains("Path traversal"), result.content());
        assertFalse(
                Files.exists(tempDir.getParent().resolve("escape.txt")),
                "Should not have written outside workspace");
    }

    @Test
    void tooManyFilesReturnsError() {
        List<Object> files = new ArrayList<>();
        for (int i = 0; i <= BatchWriteTool.MAX_FILES; i++) {
            files.add(fileEntry("file" + i + ".txt", "x"));
        }
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir));
        assertTrue(result.isError(), result.content());
        assertTrue(result.content().contains("Too many files"), result.content());
    }

    @Test
    void missingFilesParamReturnsError() {
        ToolResult result = tool.execute(Map.of(), ctx(tempDir));
        assertTrue(result.isError(), result.content());
        assertTrue(result.content().contains("'files' is required"), result.content());
    }

    @Test
    void emptyFilesArrayReturnsError() {
        ToolResult result = tool.execute(Map.of("files", List.of()), ctx(tempDir));
        assertTrue(result.isError(), result.content());
    }

    @Test
    void missingPathFieldReturnsError() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("content", "x");
        ToolResult result = tool.execute(Map.of("files", List.of(entry)), ctx(tempDir));
        assertTrue(result.isError(), result.content());
        assertTrue(result.content().contains("'path'"), result.content());
    }

    @Test
    void missingContentFieldReturnsError() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("path", "file.txt");
        ToolResult result = tool.execute(Map.of("files", List.of(entry)), ctx(tempDir));
        assertTrue(result.isError(), result.content());
        assertTrue(result.content().contains("'content'"), result.content());
    }

    @Test
    void returnsPerFileBytesAndStatus() throws IOException {
        List<Object> files = List.of(fileEntry("counted.txt", "abc"));
        ToolResult result = tool.execute(Map.of("files", files), ctx(tempDir));
        assertFalse(result.isError(), result.content());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fileResults =
                (List<Map<String, Object>>) result.metadata().get("files");
        assertNotNull(fileResults);
        assertEquals(1, fileResults.size());
        assertEquals("counted.txt", fileResults.get(0).get("path"));
        assertEquals("written", fileResults.get(0).get("status"));
        assertEquals(3, fileResults.get(0).get("bytes")); // "abc" = 3 bytes
    }
}
