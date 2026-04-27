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
import io.kairo.api.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Generates a unified diff between two files (or inline text content).
 *
 * <p>Uses an inline Myers diff algorithm — no external libraries required.
 */
@Tool(
        name = "diff",
        description =
                "Generate a unified diff between two files or text snippets. Pass file paths or"
                        + " use '-' for originalPath/modifiedPath to supply inline content via"
                        + " originalContent/modifiedContent.",
        category = ToolCategory.FILE_AND_CODE)
public class DiffTool implements ToolHandler {

    private static final int DEFAULT_CONTEXT_LINES = 3;

    @ToolParam(
            description = "Path of the original file, or '-' to use originalContent",
            required = true)
    private String originalPath;

    @ToolParam(
            description = "Path of the modified file, or '-' to use modifiedContent",
            required = true)
    private String modifiedPath;

    @ToolParam(description = "Inline original text (used when originalPath is '-')")
    private String originalContent;

    @ToolParam(description = "Inline modified text (used when modifiedPath is '-')")
    private String modifiedContent;

    @ToolParam(description = "Number of context lines around each change (default 3)")
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

        if (origPath == null || origPath.isBlank()) {
            return error("Parameter 'originalPath' is required");
        }
        if (modPath == null || modPath.isBlank()) {
            return error("Parameter 'modifiedPath' is required");
        }

        int ctx =
                input.get("contextLines") instanceof Number n
                        ? Math.max(0, n.intValue())
                        : DEFAULT_CONTEXT_LINES;

        List<String> origLines;
        List<String> modLines;
        String origLabel;
        String modLabel;

        try {
            if ("-".equals(origPath)) {
                String content = input.get("originalContent") instanceof String s ? s : "";
                origLines = splitLines(content);
                origLabel = "original";
            } else {
                Path file = workspaceRoot.resolve(origPath);
                if (!Files.exists(file)) return error("File not found: " + origPath);
                origLines = Files.readAllLines(file, StandardCharsets.UTF_8);
                origLabel = origPath;
            }

            if ("-".equals(modPath)) {
                String content = input.get("modifiedContent") instanceof String s ? s : "";
                modLines = splitLines(content);
                modLabel = "modified";
            } else {
                Path file = workspaceRoot.resolve(modPath);
                if (!Files.exists(file)) return error("File not found: " + modPath);
                modLines = Files.readAllLines(file, StandardCharsets.UTF_8);
                modLabel = modPath;
            }
        } catch (IOException e) {
            return error("Failed to read file: " + e.getMessage());
        }

        String diff = buildUnifiedDiff(origLines, modLines, origLabel, modLabel, ctx);
        boolean noDiff = diff.isEmpty();
        return new ToolResult(
                "diff", noDiff ? "No differences found" : diff, false, Map.of("hasDiff", !noDiff));
    }

    // ---- Myers diff + unified diff formatter ----

    private static List<String> splitLines(String text) {
        if (text.isEmpty()) return List.of();
        return Arrays.asList(text.split("\n", -1));
    }

    /** Build a unified diff. Returns an empty string when there are no differences. */
    static String buildUnifiedDiff(
            List<String> orig, List<String> mod, String origLabel, String modLabel, int ctx) {

        // Each op: kind (0=equal,1=delete,2=insert), origLine (-1 if insert), modLine (-1 if
        // delete)
        List<int[]> ops = diffOps(orig, mod);

        // Find positions of all non-equal ops
        boolean anyChange = ops.stream().anyMatch(op -> op[0] != 0);
        if (!anyChange) return "";

        // Group ops into hunks separated by more than 2*ctx equal lines
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(origLabel).append('\n');
        sb.append("+++ ").append(modLabel).append('\n');

        // Compute hunk boundaries using a sliding window over op indices
        List<int[]> changeIdxs = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i)[0] != 0) changeIdxs.add(new int[] {i});
        }

        int i = 0;
        while (i < changeIdxs.size()) {
            int hunkStart = changeIdxs.get(i)[0];
            int hunkEnd = changeIdxs.get(i)[0];

            // Merge nearby changes within 2*ctx distance
            int j = i + 1;
            while (j < changeIdxs.size() && changeIdxs.get(j)[0] - hunkEnd <= 2 * ctx) {
                hunkEnd = changeIdxs.get(j)[0];
                j++;
            }
            i = j;

            // Expand by context
            int opStart = Math.max(0, hunkStart - ctx);
            int opEnd = Math.min(ops.size() - 1, hunkEnd + ctx);

            // Calculate hunk header numbers
            int origStart = origLineOf(ops, opStart, true);
            int modStart = modLineOf(ops, opStart, true);
            int origCount = 0, modCount = 0;
            for (int k = opStart; k <= opEnd; k++) {
                int[] op = ops.get(k);
                if (op[0] == 0 || op[0] == 1) origCount++;
                if (op[0] == 0 || op[0] == 2) modCount++;
            }

            sb.append(
                    String.format(
                            "@@ -%d,%d +%d,%d @@%n",
                            origStart + 1, origCount, modStart + 1, modCount));

            for (int k = opStart; k <= opEnd; k++) {
                int[] op = ops.get(k);
                String line = op[0] == 1 ? orig.get(op[1]) : mod.get(op[2]);
                char prefix = op[0] == 0 ? ' ' : op[0] == 1 ? '-' : '+';
                sb.append(prefix).append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static int origLineOf(List<int[]> ops, int opIdx, boolean first) {
        for (int i = first ? opIdx : ops.size() - 1;
                first ? i < ops.size() : i >= 0;
                i += first ? 1 : -1) {
            if (ops.get(i)[1] >= 0) return ops.get(i)[1];
        }
        return 0;
    }

    private static int modLineOf(List<int[]> ops, int opIdx, boolean first) {
        for (int i = first ? opIdx : ops.size() - 1;
                first ? i < ops.size() : i >= 0;
                i += first ? 1 : -1) {
            if (ops.get(i)[2] >= 0) return ops.get(i)[2];
        }
        return 0;
    }

    /**
     * Run Myers diff and return a sequence of operations. Each op is an int[3]: [kind, origIdx,
     * modIdx] where kind: 0=equal, 1=delete, 2=insert; -1 for absent index.
     */
    static List<int[]> diffOps(List<String> orig, List<String> mod) {
        int n = orig.size();
        int m = mod.size();

        if (n == 0 && m == 0) return List.of();

        int max = n + m;
        int[] v = new int[2 * max + 2];
        List<int[]> trace = new ArrayList<>();

        boolean done = false;
        for (int d = 0; d <= max && !done; d++) {
            trace.add(Arrays.copyOf(v, 2 * max + 2));
            for (int k = -d; k <= d; k += 2) {
                int idx = k + max;
                int x;
                if (k == -d || (k != d && v[idx - 1] < v[idx + 1])) {
                    x = v[idx + 1];
                } else {
                    x = v[idx - 1] + 1;
                }
                int y = x - k;
                while (x < n && y < m && orig.get(x).equals(mod.get(y))) {
                    x++;
                    y++;
                }
                v[idx] = x;
                if (x >= n && y >= m) {
                    done = true;
                    break;
                }
            }
        }

        // Backtrack to build the edit script
        List<int[]> path = new ArrayList<>();
        int x = n, y = m;
        for (int d = trace.size() - 1; d >= 0; d--) {
            int[] vd = trace.get(d);
            int k = x - y;
            int idx = k + max;
            int prevK;
            if (k == -d || (k != d && vd[idx - 1] < vd[idx + 1])) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }
            int prevX = vd[prevK + max];
            int prevY = prevX - prevK;

            // Diagonal (equal) moves
            while (x > prevX && y > prevY) {
                x--;
                y--;
                path.add(0, new int[] {0, x, y});
            }
            if (d > 0) {
                if (x == prevX) {
                    y--;
                    path.add(0, new int[] {2, -1, y}); // insert
                } else {
                    x--;
                    path.add(0, new int[] {1, x, -1}); // delete
                }
            }
        }
        return path;
    }

    private ToolResult error(String msg) {
        return new ToolResult("diff", msg, true, Map.of());
    }
}
