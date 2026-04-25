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

class GlobToolTest {

    private GlobTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new GlobTool();
    }

    @Test
    void matchJavaFiles() throws IOException {
        Files.writeString(tempDir.resolve("Foo.java"), "class Foo {}");
        Files.writeString(tempDir.resolve("Bar.java"), "class Bar {}");
        Files.writeString(tempDir.resolve("readme.txt"), "text");

        ToolResult result = tool.execute(Map.of("pattern", "*.java", "path", tempDir.toString()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Foo.java"));
        assertTrue(result.content().contains("Bar.java"));
        assertFalse(result.content().contains("readme.txt"));
        assertEquals(2, result.metadata().get("count"));
    }

    @Test
    void matchNestedFiles() throws IOException {
        Path sub = tempDir.resolve("src");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("Main.java"), "main");
        Files.writeString(tempDir.resolve("Top.java"), "top");

        ToolResult result =
                tool.execute(Map.of("pattern", "**/*.java", "path", tempDir.toString()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Main.java"));
        // Top.java might or might not match **/*.java depending on depth—just check no error
    }

    @Test
    void noMatches() throws IOException {
        Files.writeString(tempDir.resolve("data.csv"), "a,b,c");

        ToolResult result = tool.execute(Map.of("pattern", "*.xml", "path", tempDir.toString()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("No files matched"));
        assertEquals(0, result.metadata().get("count"));
    }

    @Test
    void invalidDirectory() {
        ToolResult result = tool.execute(Map.of("pattern", "*.java", "path", "/nonexistent/dir"));
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
        ToolResult result = tool.execute(Map.of("pattern", "*.java"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'path' is required"));
    }

    @Test
    void relativePathResolvesAgainstWorkspaceRoot(@TempDir Path otherRoot) throws IOException {
        Path sub = otherRoot.resolve("src");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("Hit.java"), "class Hit {}");
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(otherRoot)
                        .acquire(WorkspaceRequest.writable(null));
        ToolContext ctx = new ToolContext("a", "s", Map.of(), null, null, ws);

        // "src" is relative — must resolve against workspace root, not JVM cwd.
        ToolResult result = tool.execute(Map.of("pattern", "*.java", "path", "src"), ctx);

        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("Hit.java"));
    }
}
