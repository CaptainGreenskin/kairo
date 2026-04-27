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

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Compares two files (or inline content) and outputs a standard unified diff. Uses an inline O(nd)
 * Myers diff algorithm — no external dependencies.
 */
@Tool(
        name = "diff",
        description =
                "Generate a unified diff between two files or inline content. "
                        + "Set originalPath or modifiedPath to '-' and supply the corresponding "
                        + "originalContent / modifiedContent to diff inline text.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.READ_ONLY)
public class DiffTool implements ToolHandler {

    @ToolParam(
            description = "Path to the original file, or '-' to use originalContent",
            required = true)
    private String originalPath;

    @ToolParam(
            description = "Path to the modified file, or '-' to use modifiedContent",
            required = true)
    private String modifiedPath;

    @ToolParam(description = "Inline original content (used when originalPath is '-')")
    private String originalContent;

    @ToolParam(description = "Inline modified content (used when modifiedPath is '-')")
    private String modifiedContent;

    @ToolParam(description = "Number of context lines around each change (default: 3)")
    private Integer contextLines;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        return doExecute(input, Workspace.cwd().root());
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        return doExecute(input, context.workspace().root());
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String origPath = (String) input.get("originalPath");
        String modPath = (String) input.get("modifiedPath");
        String origInline = (String) input.get("originalContent");
        String modInline = (String) input.get("modifiedContent");
        int ctx =
                input.get("contextLines") != null
                        ? ((Number) input.get("contextLines")).intValue()
                        : 3;

        if (origPath == null || origPath.isBlank()) {
            return error("Parameter 'originalPath' is required");
        }
        if (modPath == null || modPath.isBlank()) {
            return error("Parameter 'modifiedPath' is required");
        }

        String[] origLines, modLines;
        try {
            origLines = loadLines(origPath, origInline, workspaceRoot, "original");
            modLines = loadLines(modPath, modInline, workspaceRoot, "modified");
        } catch (IOException e) {
            return error(e.getMessage());
        }

        String diff = unifiedDiff(origPath, modPath, origLines, modLines, ctx);
        boolean noDiff = diff.isEmpty();
        return new ToolResult(
                "diff",
                noDiff ? "No differences found" : diff,
                false,
                Map.of("originalPath", origPath, "modifiedPath", modPath, "hasDiff", !noDiff));
    }

    // ─── File / inline loading ────────────────────────────────────────────────

    private static String[] loadLines(String pathParam, String inline, Path root, String label)
            throws IOException {
        if ("-".equals(pathParam)) {
            return splitLines(inline != null ? inline : "");
        }
        Path resolved = root.resolve(pathParam).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new IOException("Path traversal not allowed for " + label + ": " + pathParam);
        }
        if (!Files.exists(resolved)) {
            throw new IOException("File not found (" + label + "): " + pathParam);
        }
        return splitLines(Files.readString(resolved, StandardCharsets.UTF_8));
    }

    static String[] splitLines(String text) {
        if (text.isEmpty()) return new String[0];
        String[] parts = text.split("\n", -1);
        // trailing newline → drop phantom empty last element
        if (parts[parts.length - 1].isEmpty()) {
            return Arrays.copyOf(parts, parts.length - 1);
        }
        return parts;
    }

    // ─── Unified diff formatter ───────────────────────────────────────────────

    /**
     * Returns a unified diff string (empty string if no differences). Each edit block is int[4] =
     * {aStart, aLen, bStart, bLen} (0-indexed, lengths).
     */
    static String unifiedDiff(String origLabel, String modLabel, String[] a, String[] b, int ctx) {
        List<int[]> edits = myersDiff(a, b);
        if (edits.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(origLabel).append('\n');
        sb.append("+++ ").append(modLabel).append('\n');

        // Group edits into hunk windows
        int i = 0;
        while (i < edits.size()) {
            int[] first = edits.get(i);

            // Compute bOffset for this window: sum of (bLen-aLen) for all prior edits
            int bOffset = 0;
            for (int k = 0; k < i; k++) {
                bOffset += edits.get(k)[3] - edits.get(k)[1];
            }

            int aWinStart = Math.max(0, first[0] - ctx);
            int bWinStart = Math.max(0, first[2] - ctx);

            // Expand to merge edits within 2*ctx gap
            int j = i;
            while (j + 1 < edits.size()) {
                int[] cur = edits.get(j);
                int[] nxt = edits.get(j + 1);
                if (nxt[0] - (cur[0] + cur[1]) <= 2 * ctx) j++;
                else break;
            }

            int[] last = edits.get(j);
            int aWinEnd = Math.min(a.length, last[0] + last[1] + ctx);
            // Compute bWinEnd accounting for all edits in the hunk
            int bDelta = bOffset;
            for (int k = i; k <= j; k++) bDelta += edits.get(k)[3] - edits.get(k)[1];
            int bWinEnd = Math.min(b.length, aWinEnd + bDelta);

            // Build hunk lines
            int ai = aWinStart, bi = bWinStart;
            List<String> lines = new ArrayList<>();
            for (int k = i; k <= j; k++) {
                int[] e = edits.get(k);
                // context before edit
                while (ai < e[0]) {
                    lines.add(" " + a[ai++]);
                    bi++;
                }
                // deletions
                for (int d = e[0]; d < e[0] + e[1]; d++) lines.add("-" + a[d]);
                ai = e[0] + e[1];
                // insertions
                for (int d = e[2]; d < e[2] + e[3]; d++) lines.add("+" + b[d]);
                bi = e[2] + e[3];
            }
            // trailing context
            while (ai < aWinEnd) {
                lines.add(" " + a[ai++]);
                bi++;
            }

            int origCount = aWinEnd - aWinStart;
            int modCount = bWinEnd - bWinStart;
            sb.append("@@ -")
                    .append(hunkCoord(aWinStart + 1, origCount))
                    .append(" +")
                    .append(hunkCoord(bWinStart + 1, modCount))
                    .append(" @@\n");
            for (String line : lines) sb.append(line).append('\n');

            i = j + 1;
        }

        // strip trailing newline
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private static String hunkCoord(int start, int count) {
        // count==0: standard unified diff uses 0-based position with explicit ",0"
        if (count == 0) return (start - 1) + ",0";
        return count == 1 ? String.valueOf(start) : start + "," + count;
    }

    // ─── Myers O(nd) diff ────────────────────────────────────────────────────

    /**
     * Returns minimal edit blocks {aStart, aLen, bStart, bLen} using Myers O(nd) algorithm. Empty
     * list means sequences are equal.
     */
    static List<int[]> myersDiff(String[] a, String[] b) {
        int n = a.length, m = b.length;
        if (n == 0 && m == 0) return List.of();
        if (n == 0) return List.of(new int[] {0, 0, 0, m});
        if (m == 0) return List.of(new int[] {0, n, 0, 0});

        int max = n + m;
        int offset = max + 1;
        int[] v = new int[2 * offset + 2];
        List<int[]> trace = new ArrayList<>();

        outer:
        for (int d = 0; d <= max; d++) {
            trace.add(v.clone()); // snapshot before depth-d moves
            for (int k = -d; k <= d; k += 2) {
                int kidx = k + offset;
                int x;
                if (k == -d || (k != d && v[kidx - 1] < v[kidx + 1])) {
                    x = v[kidx + 1]; // insert from k+1 diagonal
                } else {
                    x = v[kidx - 1] + 1; // delete from k-1 diagonal
                }
                int y = x - k;
                while (x < n && y < m && a[x].equals(b[y])) {
                    x++;
                    y++;
                }
                v[kidx] = x;
                if (x >= n && y >= m) {
                    break outer; // do NOT save extra snapshot; trace[d] already exists
                }
            }
        }

        return buildEdits(a, b, trace, offset);
    }

    /** Backtrack through trace snapshots to reconstruct edit blocks. */
    private static List<int[]> buildEdits(String[] a, String[] b, List<int[]> trace, int offset) {
        int x = a.length, y = b.length;
        int D = trace.size() - 1; // depth at which solution was found

        // Collect individual moves in reverse, each: {aStart, aLen, bStart, bLen}
        List<int[]> moves = new ArrayList<>();

        for (int d = D; d >= 1 && (x > 0 || y > 0); d--) {
            int[] v = trace.get(d); // V before depth-d's inner loop (same V used for decisions)
            int k = x - y;
            int kidx = k + offset;

            int prevK;
            if (k == -d || (k != d && v[kidx - 1] < v[kidx + 1])) {
                prevK = k + 1; // insert: came from k+1 diagonal
            } else {
                prevK = k - 1; // delete: came from k-1 diagonal
            }

            int prevX = v[prevK + offset];
            int prevY = prevX - prevK;

            if (prevK == k - 1) {
                // delete: a[prevX] was removed; snake takes us from (prevX+1, prevY) to (x, y)
                moves.add(new int[] {prevX, 1, prevY, 0});
            } else {
                // insert: b[prevY] was added; snake takes us from (prevX, prevY+1) to (x, y)
                moves.add(new int[] {prevX, 0, prevY, 1});
            }

            x = prevX;
            y = prevY;
        }

        Collections.reverse(moves);
        return mergeContiguous(moves);
    }

    /**
     * Merges adjacent single-line moves into contiguous edit blocks. Two moves are adjacent if the
     * next move's aStart == prev.aStart+aLen AND bStart == prev.bStart+bLen.
     */
    private static List<int[]> mergeContiguous(List<int[]> moves) {
        if (moves.isEmpty()) return List.of();
        List<int[]> result = new ArrayList<>();
        int[] cur = moves.get(0).clone();
        for (int i = 1; i < moves.size(); i++) {
            int[] e = moves.get(i);
            if (e[0] == cur[0] + cur[1] && e[2] == cur[2] + cur[3]) {
                cur[1] += e[1];
                cur[3] += e[3];
            } else {
                result.add(cur);
                cur = e.clone();
            }
        }
        result.add(cur);
        return result;
    }

    private ToolResult error(String msg) {
        return new ToolResult("diff", msg, true, Map.of());
    }
}
