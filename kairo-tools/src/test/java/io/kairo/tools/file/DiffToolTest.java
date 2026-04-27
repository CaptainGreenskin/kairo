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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiffToolTest {

    private DiffTool tool;

    @TempDir Path tempDir;

    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        tool = new DiffTool();
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(tempDir)
                        .acquire(WorkspaceRequest.writable(null));
        ctx = new ToolContext("a", "s", Map.of(), null, null, ws);
    }

    // --- inline content tests ---

    @Test
    void identicalContent_returnsNoDiff() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "-",
                                "modifiedPath", "-",
                                "originalContent", "hello\nworld\n",
                                "modifiedContent", "hello\nworld\n"),
                        ctx);
        assertFalse(result.isError());
        assertEquals("No differences found", result.content());
        assertEquals(Boolean.FALSE, result.metadata().get("hasDiff"));
    }

    @Test
    void addedLine_showsPlusPrefix() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "-",
                                "modifiedPath", "-",
                                "originalContent", "line1\nline2\n",
                                "modifiedContent", "line1\nline2\nline3\n"),
                        ctx);
        assertFalse(result.isError());
        String diff = result.content();
        assertTrue(diff.contains("+line3"), "should contain +line3, got:\n" + diff);
        assertEquals(Boolean.TRUE, result.metadata().get("hasDiff"));
    }

    @Test
    void deletedLine_showsMinusPrefix() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "-",
                                "modifiedPath", "-",
                                "originalContent", "alpha\nbeta\ngamma\n",
                                "modifiedContent", "alpha\ngamma\n"),
                        ctx);
        assertFalse(result.isError());
        String diff = result.content();
        assertTrue(diff.contains("-beta"), "should contain -beta, got:\n" + diff);
        assertFalse(diff.contains("+beta"), "should not contain +beta");
    }

    @Test
    void changedLine_showsBothMinusAndPlus() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "-",
                                "modifiedPath", "-",
                                "originalContent", "foo\nbar\nbaz\n",
                                "modifiedContent", "foo\nQUX\nbaz\n"),
                        ctx);
        assertFalse(result.isError());
        String diff = result.content();
        assertTrue(diff.contains("-bar"), "should contain -bar, got:\n" + diff);
        assertTrue(diff.contains("+QUX"), "should contain +QUX, got:\n" + diff);
    }

    @Test
    void contextLines0_onlyChangedLines() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "-",
                                "modifiedPath", "-",
                                "originalContent", "a\nb\nc\nd\ne\n",
                                "modifiedContent", "a\nb\nX\nd\ne\n",
                                "contextLines", 0),
                        ctx);
        assertFalse(result.isError());
        String diff = result.content();
        assertTrue(diff.contains("-c"), "expected -c");
        assertTrue(diff.contains("+X"), "expected +X");
        assertFalse(diff.contains(" a"), "no context line a");
        assertFalse(diff.contains(" e"), "no context line e");
    }

    @Test
    void multipleChanges_separateHunks() {
        // Change at line 1 and line 8 — separated by enough context-free lines
        String orig = "L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8\nL9\nL10\n";
        String mod = "X1\nL2\nL3\nL4\nL5\nL6\nL7\nX8\nL9\nL10\n";
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath",
                                "-",
                                "modifiedPath",
                                "-",
                                "originalContent",
                                orig,
                                "modifiedContent",
                                mod,
                                "contextLines",
                                1),
                        ctx);
        assertFalse(result.isError());
        String diff = result.content();
        // Two hunks should produce two @@ headers
        long hunkCount = diff.lines().filter(l -> l.startsWith("@@")).count();
        assertEquals(2, hunkCount, "expected 2 hunks, got diff:\n" + diff);
    }

    @Test
    void unifiedDiffHeader_containsLabels() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "-",
                                "modifiedPath", "-",
                                "originalContent", "old\n",
                                "modifiedContent", "new\n"),
                        ctx);
        String diff = result.content();
        assertTrue(diff.startsWith("--- original\n"), "missing --- header");
        assertTrue(diff.contains("+++ modified\n"), "missing +++ header");
    }

    @Test
    void emptyToContent_allInserts() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "-",
                                "modifiedPath", "-",
                                "originalContent", "",
                                "modifiedContent", "new line\n"),
                        ctx);
        assertFalse(result.isError());
        assertTrue(result.content().contains("+new line"));
    }

    @Test
    void contentToEmpty_allDeletes() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "-",
                                "modifiedPath", "-",
                                "originalContent", "gone\n",
                                "modifiedContent", ""),
                        ctx);
        assertFalse(result.isError());
        assertTrue(result.content().contains("-gone"));
    }

    // --- file-based tests ---

    @Test
    void fileNotFound_returnsError() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "no-such-file.txt",
                                "modifiedPath", "-",
                                "modifiedContent", "x"),
                        ctx);
        assertTrue(result.isError());
        assertTrue(result.content().contains("no-such-file.txt"));
    }

    @Test
    void fileVsInlineContent() throws IOException {
        Path origFile = tempDir.resolve("orig.txt");
        Files.writeString(origFile, "line1\nline2\n");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "originalPath", "orig.txt",
                                "modifiedPath", "-",
                                "modifiedContent", "line1\nlineX\n"),
                        ctx);
        assertFalse(result.isError());
        String diff = result.content();
        assertTrue(diff.contains("-line2"), "expected -line2, got:\n" + diff);
        assertTrue(diff.contains("+lineX"), "expected +lineX, got:\n" + diff);
        // orig label should be the file name
        assertTrue(diff.contains("--- orig.txt"), "expected --- orig.txt, got:\n" + diff);
    }

    @Test
    void twoFiles_identical_noDiff() throws IOException {
        Path a = tempDir.resolve("a.txt");
        Path b = tempDir.resolve("b.txt");
        Files.writeString(a, "same\ncontent\n");
        Files.writeString(b, "same\ncontent\n");

        ToolResult result =
                tool.execute(Map.of("originalPath", "a.txt", "modifiedPath", "b.txt"), ctx);
        assertFalse(result.isError());
        assertEquals("No differences found", result.content());
    }

    // --- unit-level diffOps tests ---

    @Test
    void diffOps_noChanges_allEqual() {
        List<String> lines = List.of("a", "b", "c");
        List<int[]> ops = DiffTool.diffOps(lines, lines);
        assertTrue(ops.stream().allMatch(op -> op[0] == 0), "all ops should be equal");
    }

    @Test
    void diffOps_singleInsert() {
        List<int[]> ops = DiffTool.diffOps(List.of("a", "c"), List.of("a", "b", "c"));
        long insertCount = ops.stream().filter(op -> op[0] == 2).count();
        assertEquals(1, insertCount);
    }

    @Test
    void diffOps_singleDelete() {
        List<int[]> ops = DiffTool.diffOps(List.of("a", "b", "c"), List.of("a", "c"));
        long deleteCount = ops.stream().filter(op -> op[0] == 1).count();
        assertEquals(1, deleteCount);
    }

    // --- missingParam tests ---

    @Test
    void missingOriginalPath_returnsError() {
        ToolResult result = tool.execute(Map.of("modifiedPath", "x"), ctx);
        assertTrue(result.isError());
        assertTrue(result.content().contains("originalPath"));
    }

    @Test
    void missingModifiedPath_returnsError() {
        ToolResult result = tool.execute(Map.of("originalPath", "x"), ctx);
        assertTrue(result.isError());
        assertTrue(result.content().contains("modifiedPath"));
    }
}
