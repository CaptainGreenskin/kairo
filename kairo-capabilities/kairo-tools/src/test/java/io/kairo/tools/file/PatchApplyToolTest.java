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

class PatchApplyToolTest {

    private static final ToolContext CTX = new ToolContext("a", "s", Map.of());
    private PatchApplyTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new PatchApplyTool();
    }

    private ToolContext createWorkspaceContext() throws IOException {
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(tempDir)
                        .acquire(WorkspaceRequest.writable(null));
        return new ToolContext("a", "s", Map.of(), null, null, ws);
    }

    @Test
    void singleHunkPatchAppliedSuccessfully() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3\nline4\n");
        ToolContext ctx = createWorkspaceContext();

        String patch =
                """
                --- a/test.txt
                +++ b/test.txt
                @@ -1,4 +1,4 @@
                 line1
                -line2
                +line2-modified
                 line3
                 line4
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertFalse(result.isError(), result.content());
        String content = Files.readString(file);
        assertEquals("line1\nline2-modified\nline3\nline4\n", content);
        @SuppressWarnings("unchecked")
        var files = (java.util.List<String>) result.metadata().get("files");
        assertEquals(1, files.size());
        assertTrue(files.get(0).endsWith("test.txt"));
    }

    @Test
    void multiHunkPatchAppliedSuccessfully() throws IOException {
        Path file = tempDir.resolve("multi.txt");
        Files.writeString(file, "a\nb\nc\nd\ne\nf\n");
        ToolContext ctx = createWorkspaceContext();

        String patch =
                """
                --- a/multi.txt
                +++ b/multi.txt
                @@ -1,3 +1,3 @@
                 a
                -b
                +B
                 c
                @@ -4,3 +4,3 @@
                 d
                -e
                +E
                 f
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertFalse(result.isError(), result.content());
        String content = Files.readString(file);
        assertEquals("a\nB\nc\nd\nE\nf\n", content);
    }

    @Test
    void emptyPatchContentReturnsError() {
        ToolResult result = tool.execute(Map.of("patchContent", ""), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("patchContent"));
    }

    @Test
    void blankPatchContentReturnsError() {
        ToolResult result = tool.execute(Map.of("patchContent", "   "), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("patchContent"));
    }

    @Test
    void nullPatchContentReturnsError() {
        ToolResult result = tool.execute(Map.of(), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("patchContent"));
    }

    @Test
    void targetFileNotExistsReturnsError() throws IOException {
        ToolContext ctx = createWorkspaceContext();

        String patch =
                """
                --- a/missing.txt
                +++ b/missing.txt
                @@ -1 +1 @@
                -old
                +new
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertTrue(result.isError());
        assertTrue(
                result.content().contains("context did not match")
                        || result.content().contains("IO error"));
    }

    @Test
    void hunkNotMatchingReturnsErrorAndFileUnchanged() throws IOException {
        Path file = tempDir.resolve("nomatch.txt");
        Files.writeString(file, "alpha\nbeta\ngamma\n");
        ToolContext ctx = createWorkspaceContext();

        String patch =
                """
                --- a/nomatch.txt
                +++ b/nomatch.txt
                @@ -1,3 +1,3 @@
                 non-existent
                -context-line
                +replacement
                 gamma
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertTrue(result.isError());
        String content = Files.readString(file);
        assertEquals("alpha\nbeta\ngamma\n", content, "File must be unchanged (rolled back)");
    }

    @Test
    void firstHunkSucceedsSecondFailsRollback() throws IOException {
        Path file = tempDir.resolve("atomic.txt");
        Files.writeString(file, "line1\nline2\nline3\nline4\nline5\n");
        ToolContext ctx = createWorkspaceContext();

        // First hunk matches, second hunk has wrong context
        String patch =
                """
                --- a/atomic.txt
                +++ b/atomic.txt
                @@ -1,3 +1,3 @@
                 line1
                -line2
                +line2-changed
                 line3
                @@ -4,2 +4,2 @@
                 non-existent-context
                -line5
                +line5-changed
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertTrue(result.isError());
        String content = Files.readString(file);
        assertEquals(
                "line1\nline2\nline3\nline4\nline5\n",
                content,
                "All changes must be rolled back when any hunk fails");
    }

    @Test
    void oneLineOffsetTolerance() throws IOException {
        Path file = tempDir.resolve("offset.txt");
        Files.writeString(file, "# comment\nalpha\nbeta\ngamma\n");
        ToolContext ctx = createWorkspaceContext();

        // Patch header says line 2, but actual content is at line 3 (1-line shift)
        String patch =
                """
                --- a/offset.txt
                +++ b/offset.txt
                @@ -2,3 +2,3 @@
                 alpha
                -beta
                +beta-modified
                 gamma
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertFalse(result.isError(), result.content());
        String content = Files.readString(file);
        assertEquals("# comment\nalpha\nbeta-modified\ngamma\n", content);
    }

    @Test
    void negativeOneLineOffsetTolerance() throws IOException {
        Path file = tempDir.resolve("offset2.txt");
        Files.writeString(file, "alpha\nbeta\ngamma\n");
        ToolContext ctx = createWorkspaceContext();

        // Patch header says line 3, but actual content is at line 2 (-1 shift)
        String patch =
                """
                --- a/offset2.txt
                +++ b/offset2.txt
                @@ -3,2 +3,2 @@
                 beta
                -gamma
                +gamma-modified
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertFalse(result.isError(), result.content());
        String content = Files.readString(file);
        assertEquals("alpha\nbeta\ngamma-modified\n", content);
    }

    @Test
    void patchHeaderBPrefixStripped() throws IOException {
        Path file = tempDir.resolve("header.txt");
        Files.writeString(file, "old-content\n");
        ToolContext ctx = createWorkspaceContext();

        String patch =
                """
                --- a/header.txt
                +++ b/header.txt
                @@ -1 +1 @@
                -old-content
                +new-content
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertFalse(result.isError(), result.content());
        assertEquals("new-content\n", Files.readString(file));
    }

    @Test
    void relativePathResolvesAgainstWorkspaceRoot(@TempDir Path otherRoot) throws IOException {
        Files.writeString(otherRoot.resolve("ws-file.txt"), "original\n");

        Workspace ws =
                new LocalDirectoryWorkspaceProvider(otherRoot)
                        .acquire(WorkspaceRequest.writable(null));
        ToolContext ctx = new ToolContext("a", "s", Map.of(), null, null, ws);

        String patch =
                """
                --- a/ws-file.txt
                +++ b/ws-file.txt
                @@ -1 +1 @@
                -original
                +patched
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertFalse(result.isError(), result.content());
        assertEquals("patched\n", Files.readString(otherRoot.resolve("ws-file.txt")));
    }

    @Test
    void metadataContainsPatchedFiles() throws IOException {
        Path file = tempDir.resolve("meta.txt");
        Files.writeString(file, "x\n");
        ToolContext ctx = createWorkspaceContext();

        String patch =
                """
                --- a/meta.txt
                +++ b/meta.txt
                @@ -1 +1 @@
                -x
                +y
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("meta.txt"));
        @SuppressWarnings("unchecked")
        var files = (java.util.List<String>) result.metadata().get("files");
        assertNotNull(files);
        assertEquals(1, files.size());
        assertTrue(files.get(0).endsWith("meta.txt"));
    }

    @Test
    void dryRunDoesNotModifyFiles() throws IOException {
        Path file = tempDir.resolve("dryrun.txt");
        Files.writeString(file, "before\n");
        ToolContext ctx = createWorkspaceContext();

        String patch =
                """
                --- a/dryrun.txt
                +++ b/dryrun.txt
                @@ -1 +1 @@
                -before
                +after
                """;

        ToolResult result =
                tool.execute(Map.of("patchContent", patch, "dryRun", true), ctx).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("Dry-run"));
        @SuppressWarnings("unchecked")
        var files = (java.util.List<String>) result.metadata().get("files");
        assertNotNull(files);
        assertEquals(1, files.size());
        assertTrue(files.get(0).endsWith("dryrun.txt"));
        assertEquals(true, result.metadata().get("dryRun"));
        assertEquals("before\n", Files.readString(file), "File must not be modified in dry-run");
    }

    @Test
    void offsetBeyondToleranceReturnsError() throws IOException {
        Path file = tempDir.resolve("far-offset.txt");
        Files.writeString(file, "completely\nunrelated\ncontent\n");
        ToolContext ctx = createWorkspaceContext();

        // Header points to line 1, context won't match at +/-1 offset
        String patch =
                """
                --- a/far-offset.txt
                +++ b/far-offset.txt
                @@ -1,3 +1,3 @@
                 alpha
                -beta
                +beta-new
                 gamma
                """;

        ToolResult result = tool.execute(Map.of("patchContent", patch), ctx).block();

        assertTrue(result.isError());
        assertEquals(
                "completely\nunrelated\ncontent\n",
                Files.readString(file),
                "File must remain unchanged");
    }
}
