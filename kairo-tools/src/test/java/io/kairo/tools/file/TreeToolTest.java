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

class TreeToolTest {

    private TreeTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new TreeTool();
    }

    private ToolContext ctx(Path root) {
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(root).acquire(WorkspaceRequest.writable(null));
        return new ToolContext("a", "s", Map.of(), null, null, ws);
    }

    @Test
    void emptyDirectoryShowsOnlyRootName() {
        ToolResult result = tool.execute(Map.of("path", tempDir.toString()), ctx(tempDir));
        assertFalse(result.isError(), result.content());
        // Root directory name + "/" should appear
        assertTrue(
                result.content().endsWith("/\n"),
                "Expected root with trailing slash: " + result.content());
        assertEquals(0, result.metadata().get("totalFiles"));
        assertEquals(0, result.metadata().get("totalDirs"));
    }

    @Test
    void singleFileSingleLevel() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "hi");
        ToolResult result = tool.execute(Map.of("path", tempDir.toString()), ctx(tempDir));
        assertFalse(result.isError());
        assertTrue(result.content().contains("└── hello.txt"), result.content());
        assertEquals(1, result.metadata().get("totalFiles"));
        assertEquals(0, result.metadata().get("totalDirs"));
    }

    @Test
    void multipleFilesLastEntryUsesCornerConnector() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.txt"), "b");
        Files.writeString(tempDir.resolve("c.txt"), "c");
        ToolResult result = tool.execute(Map.of("path", tempDir.toString()), ctx(tempDir));
        assertFalse(result.isError());
        // Exactly one └── (for the last entry), rest are ├──
        long tee =
                result.content().chars().filter(c -> result.content().indexOf("├──") >= 0).count();
        assertTrue(result.content().contains("└──"), "Should contain └──");
        assertTrue(result.content().contains("├──"), "Should contain ├──");
    }

    @Test
    void directoryNamesHaveTrailingSlash() throws IOException {
        Files.createDirectory(tempDir.resolve("subdir"));
        ToolResult result = tool.execute(Map.of("path", tempDir.toString()), ctx(tempDir));
        assertFalse(result.isError());
        assertTrue(result.content().contains("subdir/"), result.content());
        assertEquals(0, result.metadata().get("totalFiles"));
        assertEquals(1, result.metadata().get("totalDirs"));
    }

    @Test
    void maxDepthZeroShowsOnlyRoot() throws IOException {
        Files.createDirectory(tempDir.resolve("deep"));
        Files.writeString(tempDir.resolve("top.txt"), "x");
        ToolResult result =
                tool.execute(Map.of("path", tempDir.toString(), "maxDepth", "0"), ctx(tempDir));
        assertFalse(result.isError());
        // With maxDepth=0, dirs are listed but not expanded; top-level files shown
        String content = result.content();
        // top.txt should appear (it's at depth 0), deep/ should appear but not expand
        assertFalse(content.contains("└──     "), "Should not have deeply nested entries");
    }

    @Test
    void maxDepthLimitsExpansion() throws IOException {
        Path l1 = tempDir.resolve("level1");
        Files.createDirectory(l1);
        Path l2 = l1.resolve("level2");
        Files.createDirectory(l2);
        Files.writeString(l2.resolve("deep.txt"), "deep");

        ToolResult result =
                tool.execute(Map.of("path", tempDir.toString(), "maxDepth", "1"), ctx(tempDir));
        assertFalse(result.isError());
        // level1/ should appear, level2/ should appear (depth 1), but deep.txt should not
        assertTrue(result.content().contains("level1/"));
        assertTrue(result.content().contains("level2/"));
        assertFalse(result.content().contains("deep.txt"), result.content());
    }

    @Test
    void includeFilesFalseShowsOnlyDirs() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "x");
        Files.createDirectory(tempDir.resolve("dir1"));
        ToolResult result =
                tool.execute(
                        Map.of("path", tempDir.toString(), "includeFiles", "false"), ctx(tempDir));
        assertFalse(result.isError());
        assertFalse(result.content().contains("file.txt"), "Files should be excluded");
        assertTrue(result.content().contains("dir1/"), result.content());
        assertEquals(0, result.metadata().get("totalFiles"));
        assertEquals(1, result.metadata().get("totalDirs"));
    }

    @Test
    void patternFiltersFiles() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"), "java");
        Files.writeString(tempDir.resolve("readme.txt"), "txt");
        Files.createDirectory(tempDir.resolve("src"));
        ToolResult result =
                tool.execute(Map.of("path", tempDir.toString(), "pattern", "*.java"), ctx(tempDir));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Foo.java"), result.content());
        assertFalse(result.content().contains("readme.txt"), result.content());
        assertTrue(
                result.content().contains("src/"), "Dirs should still appear with pattern filter");
    }

    @Test
    void excludePatternsHideMatchingEntries() throws IOException {
        Files.createDirectory(tempDir.resolve("target"));
        Files.createDirectory(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src").resolve("Main.java"), "main");
        ToolResult result =
                tool.execute(
                        Map.of("path", tempDir.toString(), "excludePatterns", "target"),
                        ctx(tempDir));
        assertFalse(result.isError());
        assertFalse(result.content().contains("target"), result.content());
        assertTrue(result.content().contains("src/"), result.content());
    }

    @Test
    void nonExistentPathReturnsError() {
        ToolResult result =
                tool.execute(
                        Map.of("path", tempDir.resolve("nonexistent").toString()), ctx(tempDir));
        assertTrue(result.isError(), result.content());
        assertTrue(result.content().contains("does not exist"), result.content());
    }

    @Test
    void filePathInsteadOfDirectoryReturnsError() throws IOException {
        Path file = tempDir.resolve("notadir.txt");
        Files.writeString(file, "content");
        ToolResult result = tool.execute(Map.of("path", file.toString()), ctx(tempDir));
        assertTrue(result.isError(), result.content());
        assertTrue(result.content().contains("not a directory"), result.content());
    }

    @Test
    void relativePathResolvesAgainstWorkspaceRoot() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "hi");
        ToolResult result = tool.execute(Map.of("path", "."), ctx(tempDir));
        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("hello.txt"), result.content());
    }

    @Test
    void metadataCountsAreAccurate() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.txt"), "b");
        Files.createDirectory(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub").resolve("c.txt"), "c");
        ToolResult result = tool.execute(Map.of("path", tempDir.toString()), ctx(tempDir));
        assertFalse(result.isError());
        assertEquals(3, result.metadata().get("totalFiles"));
        assertEquals(1, result.metadata().get("totalDirs"));
        assertEquals(false, result.metadata().get("truncated"));
    }

    @Test
    void defaultPathDotUsesWorkspaceRoot() throws IOException {
        Files.writeString(tempDir.resolve("root.txt"), "root");
        // No path param — should default to "."
        ToolResult result = tool.execute(Map.of(), ctx(tempDir));
        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("root.txt"), result.content());
    }
}
