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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WriteToolTest {

    private WriteTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new WriteTool();
    }

    @Test
    void writeNewFile() throws IOException {
        Path file = tempDir.resolve("new.txt");
        ToolResult result = tool.execute(Map.of("path", file.toString(), "content", "hello world"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Successfully wrote"));
        assertEquals("hello world", Files.readString(file));
    }

    @Test
    void overwriteExistingFile() throws IOException {
        Path file = tempDir.resolve("existing.txt");
        Files.writeString(file, "old content");

        ToolResult result = tool.execute(Map.of("path", file.toString(), "content", "new content"));
        assertFalse(result.isError());
        assertEquals("new content", Files.readString(file));
    }

    @Test
    void writeCreatesParentDirectories() throws IOException {
        Path file = tempDir.resolve("a/b/c/deep.txt");
        ToolResult result =
                tool.execute(Map.of("path", file.toString(), "content", "deep content"));
        assertFalse(result.isError());
        assertTrue(Files.exists(file));
        assertEquals("deep content", Files.readString(file));
    }

    @Test
    void writeMissingPathParameter() {
        ToolResult result = tool.execute(Map.of("content", "hello"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'path' is required"));
    }

    @Test
    void writeMissingContentParameter() {
        Path file = tempDir.resolve("noContent.txt");
        ToolResult result = tool.execute(Map.of("path", file.toString()));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'content' is required"));
    }

    @Test
    void writeEmptyContent() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        ToolResult result = tool.execute(Map.of("path", file.toString(), "content", ""));
        assertFalse(result.isError());
        assertEquals("", Files.readString(file));
    }

    @Test
    void relativePathResolvesAgainstWorkspaceRoot(@TempDir Path otherRoot) throws IOException {
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(otherRoot)
                        .acquire(WorkspaceRequest.writable(null));
        ToolContext ctx = new ToolContext("a", "s", Map.of(), null, null, ws);

        ToolResult result = tool.execute(Map.of("path", "out.txt", "content", "rooted"), ctx);

        assertFalse(result.isError(), result.content());
        // File MUST land under the workspace root, not under JVM cwd.
        assertEquals("rooted", Files.readString(otherRoot.resolve("out.txt")));
    }

    @Test
    void writeReportsBytesWritten() {
        Path file = tempDir.resolve("bytes.txt");
        ToolResult result = tool.execute(Map.of("path", file.toString(), "content", "hello"));
        assertFalse(result.isError());
        assertEquals(5, result.metadata().get("bytesWritten"));
    }
}
