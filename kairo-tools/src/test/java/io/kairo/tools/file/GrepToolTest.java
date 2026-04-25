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

class GrepToolTest {

    private GrepTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new GrepTool();
    }

    @Test
    void searchSimplePattern() throws IOException {
        Files.writeString(
                tempDir.resolve("hello.txt"), "hello world\ngoodbye world\nhello again\n");

        ToolResult result = tool.execute(Map.of("pattern", "hello", "path", tempDir.toString()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("hello world"));
        assertTrue(result.content().contains("hello again"));
        assertEquals(2, result.metadata().get("count"));
    }

    @Test
    void searchRegexPattern() throws IOException {
        Files.writeString(tempDir.resolve("nums.txt"), "abc123\ndef456\nghi\n");

        ToolResult result = tool.execute(Map.of("pattern", "\\d+", "path", tempDir.toString()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("abc123"));
        assertTrue(result.content().contains("def456"));
    }

    @Test
    void noMatchesShouldReturnEmptyResult() throws IOException {
        Files.writeString(tempDir.resolve("data.txt"), "nothing special here\n");

        ToolResult result = tool.execute(Map.of("pattern", "notfound", "path", tempDir.toString()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("No matches"));
        assertEquals(0, result.metadata().get("count"));
    }

    @Test
    void searchWithGlobFilter() throws IOException {
        Files.writeString(tempDir.resolve("code.java"), "System.out.println\n");
        Files.writeString(tempDir.resolve("code.py"), "print hello\n");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "pattern", "print",
                                "path", tempDir.toString(),
                                "glob", "*.java"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("code.java"));
        assertFalse(result.content().contains("code.py"));
    }

    @Test
    void invalidRegexShouldReturnError() {
        ToolResult result = tool.execute(Map.of("pattern", "[invalid", "path", tempDir.toString()));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Invalid regex"));
    }

    @Test
    void invalidDirectoryShouldReturnError() {
        ToolResult result = tool.execute(Map.of("pattern", "test", "path", "/nonexistent/dir"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Not a directory"));
    }

    @Test
    void missingPatternParameter() {
        ToolResult result = tool.execute(Map.of("path", tempDir.toString()));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'pattern' is required"));
    }

    @Test
    void missingPathParameter() {
        ToolResult result = tool.execute(Map.of("pattern", "test"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'path' is required"));
    }

    @Test
    void relativePathResolvesAgainstWorkspaceRoot(@TempDir Path otherRoot) throws IOException {
        Path sub = otherRoot.resolve("docs");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("notes.txt"), "alpha\nbeta\nfound-me\n");
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(otherRoot)
                        .acquire(WorkspaceRequest.writable(null));
        ToolContext ctx = new ToolContext("a", "s", Map.of(), null, null, ws);

        // "docs" is relative — must resolve against workspace root.
        ToolResult result = tool.execute(Map.of("pattern", "found-me", "path", "docs"), ctx);

        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("found-me"));
    }

    @Test
    void resultIncludesFilePathAndLineNumber() throws IOException {
        Files.writeString(tempDir.resolve("example.txt"), "line one\nfind me\nline three\n");

        ToolResult result = tool.execute(Map.of("pattern", "find me", "path", tempDir.toString()));
        assertFalse(result.isError());
        // Format: filepath:lineNumber:lineContent
        assertTrue(result.content().contains(":2:find me"));
    }
}
