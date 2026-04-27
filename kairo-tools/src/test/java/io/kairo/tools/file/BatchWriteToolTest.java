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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchWriteToolTest {

    private BatchWriteTool tool;

    @TempDir Path tempDir;

    private ToolContext ctx;

    @BeforeEach
    void setUp() throws IOException {
        tool = new BatchWriteTool();
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(tempDir)
                        .acquire(WorkspaceRequest.writable(null));
        ctx = new ToolContext("a", "s", Map.of(), null, null, ws);
    }

    @Test
    void writeSingleFile() throws IOException {
        ToolResult result =
                tool.execute(
                        Map.of("files", List.of(Map.of("path", "hello.txt", "content", "hello"))),
                        ctx);
        assertFalse(result.isError());
        assertEquals("hello", Files.readString(tempDir.resolve("hello.txt")));
        assertEquals(1, result.metadata().get("successCount"));
        assertEquals(0, result.metadata().get("errorCount"));
    }

    @Test
    void writeThreeFiles() throws IOException {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "files",
                                List.of(
                                        Map.of("path", "a.txt", "content", "aaa"),
                                        Map.of("path", "b.txt", "content", "bbb"),
                                        Map.of("path", "c.txt", "content", "ccc"))),
                        ctx);
        assertFalse(result.isError());
        assertEquals("aaa", Files.readString(tempDir.resolve("a.txt")));
        assertEquals("bbb", Files.readString(tempDir.resolve("b.txt")));
        assertEquals("ccc", Files.readString(tempDir.resolve("c.txt")));
        assertEquals(3, result.metadata().get("successCount"));
        assertEquals(0, result.metadata().get("errorCount"));
    }

    @Test
    void createDirsTrue() throws IOException {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "files",
                                List.of(
                                        Map.of(
                                                "path",
                                                "deep/nested/dir/file.txt",
                                                "content",
                                                "deep")),
                                "createDirs",
                                true),
                        ctx);
        assertFalse(result.isError());
        assertTrue(Files.exists(tempDir.resolve("deep/nested/dir/file.txt")));
        assertEquals("deep", Files.readString(tempDir.resolve("deep/nested/dir/file.txt")));
    }

    @Test
    void createDirsFalse_failsWhenParentMissing() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "files",
                                List.of(Map.of("path", "nonexistent/dir/file.txt", "content", "x")),
                                "createDirs",
                                false),
                        ctx);
        assertFalse(result.isError());
        assertEquals(0, result.metadata().get("successCount"));
        assertEquals(1, result.metadata().get("errorCount"));
    }

    @Test
    void tooManyFiles_returnsError() {
        List<Map<String, String>> files = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            files.add(Map.of("path", "f" + i + ".txt", "content", "x"));
        }
        ToolResult result = tool.execute(Map.of("files", files), ctx);
        assertTrue(result.isError());
        assertTrue(result.content().contains("Too many files"));
    }

    @Test
    void blankPath_thatFileErrors_othersSucceed() throws IOException {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "files",
                                List.of(
                                        Map.of("path", "", "content", "x"),
                                        Map.of("path", "good.txt", "content", "ok"))),
                        ctx);
        assertFalse(result.isError());
        assertEquals(1, result.metadata().get("successCount"));
        assertEquals(1, result.metadata().get("errorCount"));
        assertEquals("ok", Files.readString(tempDir.resolve("good.txt")));
    }

    @Test
    void emptyContent_writesEmptyFile() throws IOException {
        ToolResult result =
                tool.execute(
                        Map.of("files", List.of(Map.of("path", "empty.txt", "content", ""))), ctx);
        assertFalse(result.isError());
        assertEquals("", Files.readString(tempDir.resolve("empty.txt")));
        assertEquals(1, result.metadata().get("successCount"));
    }

    @Test
    void pathTraversal_thatFileErrors() throws IOException {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "files",
                                List.of(
                                        Map.of("path", "../../etc/passwd", "content", "evil"),
                                        Map.of("path", "safe.txt", "content", "safe"))),
                        ctx);
        assertFalse(result.isError());
        assertEquals("safe", Files.readString(tempDir.resolve("safe.txt")));
        assertEquals(1, result.metadata().get("successCount"));
        assertEquals(1, result.metadata().get("errorCount"));
        assertTrue(result.content().contains("ERROR"));
    }

    @Test
    void successAndErrorCounts_areCorrect() throws IOException {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "files",
                                List.of(
                                        Map.of("path", "g1.txt", "content", "1"),
                                        Map.of("path", "", "content", "x"),
                                        Map.of("path", "g2.txt", "content", "2"),
                                        Map.of("path", "", "content", "y"))),
                        ctx);
        assertFalse(result.isError());
        assertEquals(2, result.metadata().get("successCount"));
        assertEquals(2, result.metadata().get("errorCount"));
    }

    @Test
    void workspaceMode_relativePathResolvesUnderRoot() throws IOException {
        ToolResult result =
                tool.execute(
                        Map.of("files", List.of(Map.of("path", "out.txt", "content", "rooted"))),
                        ctx);
        assertFalse(result.isError(), result.content());
        assertEquals("rooted", Files.readString(tempDir.resolve("out.txt")));
    }

    @Test
    void missingFilesParameter_returnsError() {
        ToolResult result = tool.execute(Map.of(), ctx);
        assertTrue(result.isError());
        assertTrue(result.content().contains("'files'"));
    }

    @Test
    void emptyFilesList_returnsError() {
        ToolResult result = tool.execute(Map.of("files", List.of()), ctx);
        assertTrue(result.isError());
        assertTrue(result.content().contains("'files'"));
    }

    @Test
    void resultContainsOkAndErrorLines() throws IOException {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "files",
                                List.of(
                                        Map.of("path", "good.txt", "content", "hi"),
                                        Map.of("path", "", "content", "bad"))),
                        ctx);
        assertFalse(result.isError());
        assertTrue(result.content().contains("[OK]"));
        assertTrue(result.content().contains("[ERROR]"));
    }
}
