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

class DiffToolTest {

    private DiffTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new DiffTool();
    }

    @Test
    void identicalTextReturnsEmptyDiff() {
        String text = "line1\nline2\nline3\n";
        ToolResult result = tool.execute(Map.of("a", text, "b", text));

        assertFalse(result.isError());
        assertTrue(result.content().isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.metadata();
        assertTrue((Boolean) metadata.get("identical"));
        assertEquals(0, metadata.get("hunks"));
    }

    @Test
    void emptyInputsAreIdentical() {
        ToolResult result = tool.execute(Map.of("a", "", "b", ""));

        assertFalse(result.isError());
        assertTrue(result.content().isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.metadata();
        assertTrue((Boolean) metadata.get("identical"));
    }

    @Test
    void singleLineReplacementGeneratesHunk() {
        String a = "hello\nworld\n";
        String b = "hello\nkairo\n";
        ToolResult result = tool.execute(Map.of("a", a, "b", b));

        assertFalse(result.isError());
        String diff = result.content();
        assertFalse(diff.isEmpty());
        assertTrue(diff.contains("@@ -1,2 +1,2 @@"));
        assertTrue(diff.contains(" hello"));
        assertTrue(diff.contains("-world"));
        assertTrue(diff.contains("+kairo"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.metadata();
        assertFalse((Boolean) metadata.get("identical"));
        assertEquals(1, metadata.get("hunks"));
    }

    @Test
    void addedLinesAppearWithPlusPrefix() {
        String a = "first\n";
        String b = "first\nsecond\nthird\n";
        ToolResult result = tool.execute(Map.of("a", a, "b", b));

        assertFalse(result.isError());
        String diff = result.content();
        assertTrue(diff.contains("+second"));
        assertTrue(diff.contains("+third"));
    }

    @Test
    void deletedLinesAppearWithMinusPrefix() {
        String a = "first\nsecond\nthird\n";
        String b = "first\n";
        ToolResult result = tool.execute(Map.of("a", a, "b", b));

        assertFalse(result.isError());
        String diff = result.content();
        assertTrue(diff.contains("-second"));
        assertTrue(diff.contains("-third"));
    }

    @Test
    void contextLinesControlsContextAroundChanges() {
        StringBuilder aSb = new StringBuilder();
        StringBuilder bSb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            aSb.append("line ").append(i).append("\n");
            bSb.append("line ").append(i).append("\n");
        }
        // Change line 10 in b
        bSb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            if (i == 10) {
                bSb.append("line MODIFIED\n");
            } else {
                bSb.append("line ").append(i).append("\n");
            }
        }

        ToolResult resultDefault = tool.execute(Map.of("a", aSb.toString(), "b", bSb.toString()));
        ToolResult resultZero =
                tool.execute(Map.of("a", aSb.toString(), "b", bSb.toString(), "contextLines", 0));

        int defaultHunkLines = countDiffLines(resultDefault.content());
        int zeroHunkLines = countDiffLines(resultZero.content());
        assertTrue(
                defaultHunkLines > zeroHunkLines,
                "Default context should produce more lines than 0 context");
    }

    @Test
    void readsFromFilesWithPathStartingWithDot() throws IOException {
        Path fileA = tempDir.resolve("a.txt");
        Path fileB = tempDir.resolve("b.txt");
        Files.writeString(fileA, "original\n");
        Files.writeString(fileB, "modified\n");

        Workspace ws =
                new LocalDirectoryWorkspaceProvider(tempDir)
                        .acquire(WorkspaceRequest.writable(null));
        ToolContext ctx = new ToolContext("a", "s", Map.of(), null, null, ws);

        ToolResult result = tool.execute(Map.of("a", "./a.txt", "b", "./b.txt"), ctx);

        assertFalse(result.isError(), result.content());
        String diff = result.content();
        assertTrue(diff.contains("-original"));
        assertTrue(diff.contains("+modified"));
    }

    @Test
    void rawStringInputNotTreatedAsFilePath() {
        ToolResult result = tool.execute(Map.of("a", "foo\n", "b", "bar\n"));

        assertFalse(result.isError());
        String diff = result.content();
        assertTrue(diff.contains("-foo"));
        assertTrue(diff.contains("+bar"));
    }

    @Test
    void customLabelsAppearInDiffHeader() {
        String a = "old\n";
        String b = "new\n";
        ToolResult result =
                tool.execute(
                        Map.of(
                                "a",
                                a,
                                "b",
                                b,
                                "aLabel",
                                "--- old_file.txt",
                                "bLabel",
                                "+++ new_file.txt"));

        assertFalse(result.isError());
        String diff = result.content();
        assertTrue(diff.contains("old_file.txt"));
        assertTrue(diff.contains("new_file.txt"));
    }

    @Test
    void trailingNewlineHandling() {
        String aNoNewline = "line1\nline2";
        String aWithNewline = "line1\nline2\n";
        String b = "line1\nchanged\n";

        ToolResult resultNoNewline = tool.execute(Map.of("a", aNoNewline, "b", b));
        ToolResult resultWithNewline = tool.execute(Map.of("a", aWithNewline, "b", b));

        assertFalse(resultNoNewline.isError());
        assertFalse(resultWithNewline.isError());
        // Both should produce valid diff output
        assertTrue(resultNoNewline.content().contains("@@"));
        assertTrue(resultWithNewline.content().contains("@@"));
    }

    @Test
    void multipleDiscontiguousChangesGenerateMultipleHunks() {
        String a = "keep1\nchange1\nkeep2\nkeep3\nchange2\nkeep4\n";
        String b = "keep1\nnew1\nkeep2\nkeep3\nnew2\nkeep4\n";
        // With 0 context, changes are far apart and should produce separate hunks
        ToolResult result = tool.execute(Map.of("a", a, "b", b, "contextLines", 0));

        assertFalse(result.isError());
        String diff = result.content();
        long hunkCount = diff.lines().filter(l -> l.startsWith("@@")).count();
        assertEquals(2, hunkCount, "Should have 2 separate hunks");
    }

    @Test
    void missingRequiredParameterReturnsError() {
        ToolResult resultA = tool.execute(Map.of("b", "something"));
        ToolResult resultB = tool.execute(Map.of("a", "something"));

        assertTrue(resultA.isError());
        assertTrue(resultA.content().contains("'a' is required"));
        assertTrue(resultB.isError());
        assertTrue(resultB.content().contains("'b' is required"));
    }

    @Test
    void nonExistentFileReturnsError() throws IOException {
        Workspace ws =
                new LocalDirectoryWorkspaceProvider(tempDir)
                        .acquire(WorkspaceRequest.writable(null));
        ToolContext ctx = new ToolContext("a", "s", Map.of(), null, null, ws);

        ToolResult result = tool.execute(Map.of("a", "./exists.txt", "b", "./missing.txt"), ctx);

        assertTrue(result.isError());
        assertTrue(result.content().contains("Failed to read"));
    }

    @Test
    void hunksMetadataIsAccurate() {
        // Create input that results in exactly 2 hunks with default context
        String a = "A\nB\nC\nD\nE\nF\nG\nH\nI\nJ\n";
        String b = "A\nX\nC\nD\nE\nF\nG\nH\nY\nJ\n";
        ToolResult result = tool.execute(Map.of("a", a, "b", b, "contextLines", 0));

        assertFalse(result.isError());
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.metadata();
        int actualHunks = (int) result.content().lines().filter(l -> l.startsWith("@@")).count();
        assertEquals(actualHunks, metadata.get("hunks"));
    }

    private int countDiffLines(String diff) {
        if (diff == null || diff.isEmpty()) return 0;
        return (int) diff.lines().count();
    }
}
