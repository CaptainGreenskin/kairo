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

    private ToolContext ctx;

    @BeforeEach
    void setUp() throws IOException {
        tool = new TreeTool();
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(tempDir)
                        .acquire(WorkspaceRequest.writable(null));
        ctx = new ToolContext("a", "s", Map.of(), null, null, ws);
    }

    @Test
    void emptyDirectory_outputsOnlyRootName() {
        Path dir = tempDir.resolve("empty");
        dir.toFile().mkdir();
        ToolResult result = tool.execute(Map.of("path", "empty"), ctx);
        assertFalse(result.isError(), result.content());
        assertTrue(result.content().startsWith("empty/"));
        // No further lines beyond the root
        assertEquals("empty/", result.content().trim());
    }

    @Test
    void singleFile_showsBranchAndFileName() throws IOException {
        Path dir = tempDir.resolve("mydir");
        dir.toFile().mkdir();
        Files.writeString(dir.resolve("hello.txt"), "");
        ToolResult result = tool.execute(Map.of("path", "mydir"), ctx);
        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("└── hello.txt"));
    }

    @Test
    void multipleFiles_lastUsesLast_othersUseBranch() throws IOException {
        Path dir = tempDir.resolve("multi");
        dir.toFile().mkdir();
        Files.writeString(dir.resolve("a.txt"), "");
        Files.writeString(dir.resolve("b.txt"), "");
        Files.writeString(dir.resolve("c.txt"), "");
        ToolResult result = tool.execute(Map.of("path", "multi"), ctx);
        String out = result.content();
        assertTrue(out.contains("├── a.txt"));
        assertTrue(out.contains("├── b.txt"));
        assertTrue(out.contains("└── c.txt"));
    }

    @Test
    void directoryName_hasSuffix() throws IOException {
        Path dir = tempDir.resolve("parent");
        Path sub = dir.resolve("child");
        Files.createDirectories(sub);
        ToolResult result = tool.execute(Map.of("path", "parent"), ctx);
        assertTrue(result.content().contains("child/"));
    }

    @Test
    void maxDepth0_doesNotExpandSubdirectories() throws IOException {
        Path dir = tempDir.resolve("deep");
        Files.createDirectories(dir.resolve("sub/nested"));
        Files.writeString(dir.resolve("sub/nested/file.txt"), "");
        ToolResult result = tool.execute(Map.of("path", "deep", "maxDepth", 0), ctx);
        String out = result.content();
        assertTrue(out.contains("sub/"));
        assertFalse(out.contains("nested"));
    }

    @Test
    void maxDepth1_expandsOnlyOneLevel() throws IOException {
        Path dir = tempDir.resolve("layered");
        Files.createDirectories(dir.resolve("a/b"));
        Files.writeString(dir.resolve("a/b/file.txt"), "");
        ToolResult result = tool.execute(Map.of("path", "layered", "maxDepth", 1), ctx);
        String out = result.content();
        assertTrue(out.contains("a/"));
        assertFalse(out.contains("file.txt"));
    }

    @Test
    void showFilesFalse_onlyShowsDirectories() throws IOException {
        Path dir = tempDir.resolve("mixed");
        dir.toFile().mkdir();
        Files.writeString(dir.resolve("file.txt"), "");
        Files.createDirectories(dir.resolve("subdir"));
        ToolResult result = tool.execute(Map.of("path", "mixed", "showFiles", false), ctx);
        String out = result.content();
        assertTrue(out.contains("subdir/"));
        assertFalse(out.contains("file.txt"));
    }

    @Test
    void patternFilter_onlyMatchingFilesShown() throws IOException {
        Path dir = tempDir.resolve("filtered");
        dir.toFile().mkdir();
        Files.writeString(dir.resolve("Main.java"), "");
        Files.writeString(dir.resolve("README.md"), "");
        Files.writeString(dir.resolve("Test.java"), "");
        ToolResult result = tool.execute(Map.of("path", "filtered", "pattern", "*.java"), ctx);
        String out = result.content();
        assertTrue(out.contains("Main.java"));
        assertTrue(out.contains("Test.java"));
        assertFalse(out.contains("README.md"));
    }

    @Test
    void patternFilter_directoriesAlwaysShown() throws IOException {
        Path dir = tempDir.resolve("withsub");
        dir.toFile().mkdir();
        Files.writeString(dir.resolve("notes.md"), "");
        Files.createDirectories(dir.resolve("src"));
        ToolResult result = tool.execute(Map.of("path", "withsub", "pattern", "*.java"), ctx);
        String out = result.content();
        assertTrue(out.contains("src/"));
        assertFalse(out.contains("notes.md"));
    }

    @Test
    void nonExistentPath_returnsError() {
        ToolResult result = tool.execute(Map.of("path", "no-such-dir"), ctx);
        assertTrue(result.isError());
        assertTrue(result.content().contains("not found"));
    }

    @Test
    void missingPathParameter_returnsError() {
        ToolResult result = tool.execute(Map.of(), ctx);
        assertTrue(result.isError());
        assertTrue(result.content().contains("'path'"));
    }

    @Test
    void workspaceMode_relativePathResolvesUnderRoot() throws IOException {
        Path sub = tempDir.resolve("ws-sub");
        sub.toFile().mkdir();
        Files.writeString(sub.resolve("f.txt"), "");
        ToolResult result = tool.execute(Map.of("path", "ws-sub"), ctx);
        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("ws-sub/"));
        assertTrue(result.content().contains("f.txt"));
    }

    @Test
    void nestedStructure_correctIndentation() throws IOException {
        Path root = tempDir.resolve("proj");
        Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(root.resolve("src/main/java/Foo.java"), "");
        Files.createDirectories(root.resolve("src/test/java"));
        Files.writeString(root.resolve("src/test/java/FooTest.java"), "");
        ToolResult result = tool.execute(Map.of("path", "proj", "maxDepth", 5), ctx);
        String out = result.content();
        assertTrue(out.contains("Foo.java"));
        assertTrue(out.contains("FooTest.java"));
        assertTrue(out.contains("main/"));
        assertTrue(out.contains("test/"));
    }
}
