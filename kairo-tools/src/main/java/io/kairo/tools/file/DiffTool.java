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

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Generates unified diffs between two texts or files (pure Java, no external diff library).
 *
 * <p>Uses a simplified Myers-style LCS-based diff algorithm to produce standard unified diff output
 * compatible with {@code patch} and most diff viewers.
 */
@Tool(
        name = "diff",
        description =
                "Generate a unified diff between two texts or files. a and b may be raw strings or"
                        + " file paths (starting with / or ./).",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.READ_ONLY)
public class DiffTool implements SyncTool {

    @Override
    public JsonSchema inputSchema() {
        java.util.Map<String, JsonSchema> props = new java.util.LinkedHashMap<>();
        props.put(
                "a",
                new JsonSchema(
                        "string",
                        null,
                        null,
                        "Left side: raw text or absolute/relative path (starts with '/' or './')."));
        props.put(
                "b",
                new JsonSchema(
                        "string", null, null, "Right side: raw text or absolute/relative path."));
        props.put(
                "aLabel",
                new JsonSchema(
                        "string",
                        null,
                        null,
                        "Optional label for the left side in the diff header."));
        props.put(
                "bLabel",
                new JsonSchema(
                        "string",
                        null,
                        null,
                        "Optional label for the right side in the diff header."));
        props.put(
                "contextLines",
                new JsonSchema(
                        "integer",
                        null,
                        null,
                        "Lines of context around each hunk. Defaults to 3."));
        return new JsonSchema("object", props, java.util.List.of("a", "b"), null);
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx.workspace().root()));
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String aInput = (String) input.get("a");
        String bInput = (String) input.get("b");
        String aLabel = input.getOrDefault("aLabel", "a").toString();
        String bLabel = input.getOrDefault("bLabel", "b").toString();
        int contextLines = 3;
        if (input.get("contextLines") != null) {
            try {
                contextLines = Integer.parseInt(input.get("contextLines").toString());
                if (contextLines < 0) contextLines = 0;
            } catch (NumberFormatException ignored) {
            }
        }

        if (aInput == null) return error("Parameter 'a' is required");
        if (bInput == null) return error("Parameter 'b' is required");

        String aText, bText;
        try {
            aText = resolveText(aInput, workspaceRoot);
            bText = resolveText(bInput, workspaceRoot);
        } catch (IOException e) {
            return error("Failed to read file: " + e.getMessage());
        }

        String diff = unifiedDiff(aText, bText, aLabel, bLabel, contextLines);
        int hunks = countHunks(diff);
        return ToolResult.success(
                "diff", diff, Map.of("hunks", hunks, "identical", diff.isEmpty()));
    }

    // ---- Text resolution ----

    private String resolveText(String input, Path workspaceRoot) throws IOException {
        String trimmed = input.trim();
        if (trimmed.startsWith("/") || trimmed.startsWith("~/")) {
            Path p =
                    trimmed.startsWith("~/")
                            ? Path.of(System.getProperty("user.home")).resolve(trimmed.substring(2))
                            : Path.of(trimmed);
            return Files.readString(p, StandardCharsets.UTF_8);
        }
        if (trimmed.startsWith("./") || trimmed.startsWith("../")) {
            return Files.readString(workspaceRoot.resolve(trimmed), StandardCharsets.UTF_8);
        }
        return input;
    }

    // ---- Unified diff generation ----

    String unifiedDiff(String aText, String bText, String aLabel, String bLabel, int ctx) {
        String[] aLines = splitLines(aText);
        String[] bLines = splitLines(bText);

        List<Edit> edits = computeEdits(aLines, bLines);
        if (edits.isEmpty()) return "";

        List<Hunk> hunks = groupIntoHunks(edits, aLines.length, bLines.length, ctx);
        if (hunks.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(aLabel).append("\n");
        sb.append("+++ ").append(bLabel).append("\n");

        for (Hunk hunk : hunks) {
            sb.append(renderHunk(hunk, aLines, bLines));
        }
        return sb.toString();
    }

    private String[] splitLines(String text) {
        if (text.isEmpty()) return new String[0];
        String[] lines = text.split("\n", -1);
        // If text ends with \n, last element is empty — drop it
        if (text.endsWith("\n") && lines.length > 0 && lines[lines.length - 1].isEmpty()) {
            return Arrays.copyOf(lines, lines.length - 1);
        }
        return lines;
    }

    // ---- Myers-style edit script ----

    /**
     * Edit describes a range in a (aStart..aEnd exclusive) replaced by b (bStart..bEnd exclusive).
     * Equal ranges are represented with aStart==aEnd and bStart==bEnd at the same positions.
     */
    private record Edit(int aStart, int aEnd, int bStart, int bEnd) {
        boolean isEqual() {
            return aStart == aEnd && bStart == bEnd;
        }
    }

    /**
     * Compute the minimal edit script using the patience-diff LCS approach (simplified to standard
     * DP LCS for correctness). Returns a list of changed Edit regions only.
     */
    private List<Edit> computeEdits(String[] a, String[] b) {
        int n = a.length, m = b.length;
        // Build LCS table
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a[i].equals(b[j])) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        // Trace back LCS to produce edits
        List<Edit> edits = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a[i].equals(b[j])) {
                i++;
                j++;
            } else {
                int aStart = i, bStart = j;
                // Advance while not in LCS
                while (i < n && j < m && !a[i].equals(b[j])) {
                    if (dp[i + 1][j] >= dp[i][j + 1]) {
                        i++;
                    } else {
                        j++;
                    }
                }
                // Drain remaining one-sided deletions/insertions
                while (i < n && (j >= m || dp[i + 1][j] >= dp[i][j])) {
                    if (i < n && j < m && a[i].equals(b[j])) break;
                    i++;
                }
                while (j < m && (i >= n || dp[i][j + 1] >= dp[i][j])) {
                    if (i < n && j < m && a[i].equals(b[j])) break;
                    j++;
                }
                if (i > aStart || j > bStart) {
                    edits.add(new Edit(aStart, i, bStart, j));
                }
            }
        }
        return edits;
    }

    // ---- Hunk grouping ----

    private record Hunk(int aStart, int aEnd, int bStart, int bEnd) {}

    private List<Hunk> groupIntoHunks(List<Edit> edits, int aLen, int bLen, int ctx) {
        List<Hunk> hunks = new ArrayList<>();
        int[] aOff = {0};
        int[] bOff = {0};

        for (int e = 0; e < edits.size(); ) {
            Edit first = edits.get(e);
            int hunkAStart = Math.max(0, first.aStart() - ctx);
            int hunkBStart = Math.max(0, first.bStart() - ctx);

            // Merge consecutive edits within 2*ctx gap
            int aEnd = first.aEnd(), bEnd = first.bEnd();
            int last = e;
            while (last + 1 < edits.size()) {
                Edit next = edits.get(last + 1);
                if (next.aStart() - aEnd <= 2 * ctx) {
                    aEnd = next.aEnd();
                    bEnd = next.bEnd();
                    last++;
                } else {
                    break;
                }
            }

            int hunkAEnd = Math.min(aLen, aEnd + ctx);
            int hunkBEnd = Math.min(bLen, bEnd + ctx);
            hunks.add(new Hunk(hunkAStart, hunkAEnd, hunkBStart, hunkBEnd));
            e = last + 1;
        }
        return hunks;
    }

    private String renderHunk(Hunk hunk, String[] a, String[] b) {
        // Re-compute edit boundaries within hunk for line attribution
        String[] aSlice = Arrays.copyOfRange(a, hunk.aStart(), hunk.aEnd());
        String[] bSlice = Arrays.copyOfRange(b, hunk.bStart(), hunk.bEnd());
        List<Edit> localEdits = computeEdits(aSlice, bSlice);

        StringBuilder sb = new StringBuilder();
        int aCount = hunk.aEnd() - hunk.aStart();
        int bCount = hunk.bEnd() - hunk.bStart();
        sb.append(
                String.format(
                        "@@ -%d,%d +%d,%d @@\n",
                        hunk.aStart() + 1, aCount, hunk.bStart() + 1, bCount));

        int ai = 0, bi = 0;
        for (Edit edit : localEdits) {
            // Context lines before edit
            while (ai < edit.aStart()) {
                sb.append(" ").append(aSlice[ai]).append("\n");
                ai++;
                bi++;
            }
            // Deleted lines
            for (int i = edit.aStart(); i < edit.aEnd(); i++) {
                sb.append("-").append(aSlice[i]).append("\n");
            }
            // Inserted lines
            for (int i = edit.bStart(); i < edit.bEnd(); i++) {
                sb.append("+").append(bSlice[i]).append("\n");
            }
            ai = edit.aEnd();
            bi = edit.bEnd();
        }
        // Trailing context
        while (ai < aSlice.length) {
            sb.append(" ").append(aSlice[ai]).append("\n");
            ai++;
        }
        return sb.toString();
    }

    private int countHunks(String diff) {
        if (diff.isEmpty()) return 0;
        int count = 0;
        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("@@")) count++;
        }
        return count;
    }

    private ToolResult error(String msg) {
        return ToolResult.error("diff", msg);
    }
}
