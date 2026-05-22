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

import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.LspService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Applies a unified diff (patch) to files in the workspace.
 *
 * <p>Supports context lines with 1-line offset tolerance. On any hunk failure the entire patch is
 * rolled back — no partial writes.
 */
@Tool(
        name = "patch_apply",
        description =
                "Apply a unified diff (patch) to files in the workspace. Supports dry-run mode."
                        + " Rolls back all changes on failure.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class PatchApplyTool implements SyncTool {

    private final PostEditDiagnosticsHook lspHook;

    public PatchApplyTool() {
        this(null);
    }

    public PatchApplyTool(LspService lspService) {
        this.lspHook = lspService == null ? null : new PostEditDiagnosticsHook(lspService);
    }

    @Override
    public JsonSchema inputSchema() {
        java.util.Map<String, JsonSchema> props = new java.util.LinkedHashMap<>();
        props.put(
                "patchContent",
                new JsonSchema(
                        "string",
                        null,
                        null,
                        "Unified-diff text to apply. Must follow `diff --git` / `--- a/...` / `+++ b/...` format."));
        props.put(
                "targetPath",
                new JsonSchema(
                        "string",
                        null,
                        null,
                        "Absolute workspace root the diff paths are relative to."));
        props.put(
                "dryRun",
                new JsonSchema(
                        "boolean", null, null, "Validate without writing. Defaults to false."));
        return new JsonSchema(
                "object", props, java.util.List.of("patchContent", "targetPath"), null);
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx.workspace().root()));
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String patchContent = (String) input.get("patchContent");
        if (patchContent == null || patchContent.isBlank()) {
            return error("Parameter 'patchContent' is required");
        }
        String targetPathOverride = (String) input.get("targetPath");
        boolean dryRun = Boolean.TRUE.equals(input.get("dryRun"));

        try {
            List<FilePatch> patches = parsePatch(patchContent, workspaceRoot, targetPathOverride);
            if (patches.isEmpty()) {
                return error("No valid file patches found in patchContent");
            }

            // Phase 1: apply all hunks in memory (rollback-safe)
            List<ApplyResult> results = new ArrayList<>();
            for (FilePatch fp : patches) {
                results.add(applyHunks(fp));
            }

            // Check for any failures
            for (ApplyResult r : results) {
                if (r.failed()) {
                    return error(
                            "Patch failed for "
                                    + r.path()
                                    + ": "
                                    + r.errorMessage()
                                    + " — no files modified");
                }
            }

            int totalApplied = results.stream().mapToInt(r -> r.appliedHunks).sum();
            int totalSkipped = results.stream().mapToInt(r -> r.skippedHunks).sum();
            List<String> modifiedPaths = results.stream().map(r -> r.path().toString()).toList();

            if (dryRun) {
                return ToolResult.success(
                        "patch_apply",
                        "Dry-run: patch can be applied successfully to "
                                + modifiedPaths.size()
                                + " file(s)",
                        Map.of(
                                "dryRun", true,
                                "files", modifiedPaths,
                                "appliedHunks", totalApplied,
                                "skippedHunks", totalSkipped));
            }

            // Phase 2: snapshot LSP baselines, write, then collect introduced diagnostics
            List<PostEditDiagnosticsHook.Token> tokens = new ArrayList<>();
            if (lspHook != null) {
                for (FilePatch p : patches) tokens.add(lspHook.beforeWrite(p.path()));
            }

            for (int i = 0; i < patches.size(); i++) {
                ApplyResult r = results.get(i);
                String joined = String.join("\n", r.newLines()) + "\n";
                Files.writeString(patches.get(i).path(), joined, StandardCharsets.UTF_8);
            }

            Map<String, List<Map<String, Object>>> diagsByPath = new HashMap<>();
            if (lspHook != null) {
                for (int i = 0; i < patches.size(); i++) {
                    String joined = String.join("\n", results.get(i).newLines()) + "\n";
                    List<Diagnostic> introduced = lspHook.afterWrite(tokens.get(i), joined);
                    if (!introduced.isEmpty()) {
                        diagsByPath.put(
                                patches.get(i).path().toString(),
                                PostEditDiagnosticsHook.toMetadata(introduced));
                    }
                }
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("files", modifiedPaths);
            metadata.put("appliedHunks", totalApplied);
            metadata.put("skippedHunks", totalSkipped);
            if (!diagsByPath.isEmpty()) metadata.put("newDiagnostics", diagsByPath);

            return ToolResult.success(
                    "patch_apply",
                    "Applied patch to " + modifiedPaths.size() + " file(s): " + modifiedPaths,
                    metadata);

        } catch (IOException e) {
            return error("IO error: " + e.getMessage());
        } catch (PatchParseException e) {
            return error("Parse error: " + e.getMessage());
        }
    }

    // ---- Parsing ----

    private List<FilePatch> parsePatch(
            String patchContent, Path workspaceRoot, String targetPathOverride)
            throws IOException, PatchParseException {
        String[] lines = patchContent.split("\n", -1);
        List<FilePatch> result = new ArrayList<>();

        int i = 0;
        while (i < lines.length) {
            // Find "--- " header
            if (!lines[i].startsWith("--- ")) {
                i++;
                continue;
            }
            if (i + 1 >= lines.length || !lines[i + 1].startsWith("+++ ")) {
                throw new PatchParseException("Expected '+++ ' after '--- ' at line " + (i + 1));
            }

            String targetFile =
                    targetPathOverride != null ? targetPathOverride : parseFilePath(lines[i + 1]);
            Path filePath = workspaceRoot.resolve(targetFile);
            i += 2;

            // Read current file content
            List<String> originalLines;
            if (Files.exists(filePath)) {
                String raw = Files.readString(filePath, StandardCharsets.UTF_8);
                originalLines = splitLines(raw);
            } else {
                originalLines = new ArrayList<>();
            }

            // Collect hunks
            List<Hunk> hunks = new ArrayList<>();
            while (i < lines.length && lines[i].startsWith("@@")) {
                HunkHeader header = parseHunkHeader(lines[i], i);
                i++;
                List<String> hunkLines = new ArrayList<>();
                while (i < lines.length
                        && !lines[i].startsWith("@@")
                        && !lines[i].startsWith("--- ")
                        && !lines[i].startsWith("diff ")) {
                    hunkLines.add(lines[i]);
                    i++;
                }
                hunks.add(new Hunk(header, hunkLines));
            }

            result.add(new FilePatch(filePath, originalLines, hunks));
        }
        return result;
    }

    private String parseFilePath(String headerLine) throws PatchParseException {
        // "+++ b/path/to/file" or "+++ path/to/file"
        String part = headerLine.substring(4).trim();
        if (part.startsWith("b/")) {
            part = part.substring(2);
        }
        // Strip timestamp suffix like "\t2025-01-01 ..."
        int tab = part.indexOf('\t');
        if (tab >= 0) {
            part = part.substring(0, tab).trim();
        }
        if (part.isBlank()) {
            throw new PatchParseException("Cannot determine target path from: " + headerLine);
        }
        return part;
    }

    private HunkHeader parseHunkHeader(String line, int lineIndex) throws PatchParseException {
        // @@ -oldStart,oldCount +newStart,newCount @@
        int at1 = line.indexOf("@@", 1);
        if (at1 < 0) {
            throw new PatchParseException(
                    "Invalid hunk header at line " + (lineIndex + 1) + ": " + line);
        }
        String inner = line.substring(3, at1).trim();
        String[] parts = inner.split(" ");
        if (parts.length < 2) {
            throw new PatchParseException("Malformed hunk header: " + line);
        }
        int oldStart = parseHunkRange(parts[0].substring(1))[0];
        int newStart = parseHunkRange(parts[1].substring(1))[0];
        return new HunkHeader(oldStart, newStart);
    }

    private int[] parseHunkRange(String range) {
        String[] parts = range.split(",");
        int start = Integer.parseInt(parts[0]);
        int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        return new int[] {start, count};
    }

    // ---- Applying ----

    private ApplyResult applyHunks(FilePatch fp) {
        List<String> lines = new ArrayList<>(fp.originalLines());
        int appliedHunks = 0;
        int skippedHunks = 0;
        // Apply hunks in order; track offset from insertions/deletions
        int lineOffset = 0;

        for (Hunk hunk : fp.hunks()) {
            int basePos = hunk.header().oldStart() - 1 + lineOffset;
            ApplyHunkResult r = tryApplyHunk(lines, hunk, basePos);
            if (r.success()) {
                lines = r.lines();
                lineOffset += r.lineDelta();
                appliedHunks++;
            } else {
                // Try with +1/-1 offset tolerance
                boolean recovered = false;
                for (int delta : new int[] {1, -1}) {
                    ApplyHunkResult retry = tryApplyHunk(lines, hunk, basePos + delta);
                    if (retry.success()) {
                        lines = retry.lines();
                        lineOffset += retry.lineDelta();
                        appliedHunks++;
                        recovered = true;
                        break;
                    }
                }
                if (!recovered) {
                    return ApplyResult.failure(
                            fp.path(),
                            "hunk at line "
                                    + hunk.header().oldStart()
                                    + " context did not match (tried ±1 offset)");
                }
            }
        }
        return ApplyResult.success(fp.path(), lines, appliedHunks, skippedHunks);
    }

    private ApplyHunkResult tryApplyHunk(List<String> lines, Hunk hunk, int basePos) {
        List<String> hunkLines = hunk.lines();
        List<String> result = new ArrayList<>(lines);

        int pos = basePos;
        int additions = 0;
        int deletions = 0;

        // Validate context lines before making any changes
        int checkPos = pos;
        for (String hl : hunkLines) {
            if (hl.isEmpty()) {
                // Treat empty line as context
                checkPos++;
                continue;
            }
            char op = hl.charAt(0);
            String content = hl.substring(1);
            if (op == ' ') {
                if (checkPos < 0
                        || checkPos >= lines.size()
                        || !lines.get(checkPos).equals(content)) {
                    return ApplyHunkResult.failure();
                }
                checkPos++;
            } else if (op == '-') {
                if (checkPos < 0
                        || checkPos >= lines.size()
                        || !lines.get(checkPos).equals(content)) {
                    return ApplyHunkResult.failure();
                }
                checkPos++;
                deletions++;
            } else if (op == '+') {
                additions++;
            }
        }

        // Apply the hunk
        List<String> after = new ArrayList<>();
        int srcPos = pos;
        for (String hl : hunkLines) {
            if (hl.isEmpty()) {
                if (srcPos < result.size()) {
                    after.add(result.get(srcPos++));
                }
                continue;
            }
            char op = hl.charAt(0);
            String content = hl.substring(1);
            if (op == ' ') {
                after.add(result.get(srcPos++));
            } else if (op == '-') {
                srcPos++; // skip old line
            } else if (op == '+') {
                after.add(content);
            }
        }

        // Rebuild full file
        List<String> output = new ArrayList<>();
        output.addAll(result.subList(0, pos));
        output.addAll(after);
        if (srcPos < result.size()) {
            output.addAll(result.subList(srcPos, result.size()));
        }

        return ApplyHunkResult.success(output, additions - deletions);
    }

    private List<String> splitLines(String content) {
        if (content.isEmpty()) return new ArrayList<>();
        String[] arr = content.split("\n", -1);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < arr.length; i++) {
            // Don't add trailing empty string from final newline
            if (i == arr.length - 1 && arr[i].isEmpty()) break;
            result.add(arr[i]);
        }
        return result;
    }

    private ToolResult error(String msg) {
        return ToolResult.error("patch_apply", msg);
    }

    // ---- Records ----

    private record FilePatch(Path path, List<String> originalLines, List<Hunk> hunks) {}

    private record HunkHeader(int oldStart, int newStart) {}

    private record Hunk(HunkHeader header, List<String> lines) {}

    private static final class ApplyResult {
        private final Path path;
        private final List<String> newLines;
        private final int appliedHunks;
        private final int skippedHunks;
        private final String errorMessage;

        private ApplyResult(
                Path path,
                List<String> newLines,
                int appliedHunks,
                int skippedHunks,
                String errorMessage) {
            this.path = path;
            this.newLines = newLines;
            this.appliedHunks = appliedHunks;
            this.skippedHunks = skippedHunks;
            this.errorMessage = errorMessage;
        }

        static ApplyResult success(
                Path path, List<String> newLines, int appliedHunks, int skippedHunks) {
            return new ApplyResult(path, newLines, appliedHunks, skippedHunks, null);
        }

        static ApplyResult failure(Path path, String msg) {
            return new ApplyResult(path, null, 0, 0, msg);
        }

        boolean failed() {
            return errorMessage != null;
        }

        Path path() {
            return path;
        }

        List<String> newLines() {
            return newLines;
        }

        String errorMessage() {
            return errorMessage;
        }
    }

    private static final class ApplyHunkResult {
        private final boolean success;
        private final List<String> lines;
        private final int lineDelta;

        private ApplyHunkResult(boolean success, List<String> lines, int lineDelta) {
            this.success = success;
            this.lines = lines;
            this.lineDelta = lineDelta;
        }

        static ApplyHunkResult success(List<String> lines, int lineDelta) {
            return new ApplyHunkResult(true, lines, lineDelta);
        }

        static ApplyHunkResult failure() {
            return new ApplyHunkResult(false, null, 0);
        }

        boolean success() {
            return success;
        }

        List<String> lines() {
            return lines;
        }

        int lineDelta() {
            return lineDelta;
        }
    }

    private static class PatchParseException extends Exception {
        PatchParseException(String message) {
            super(message);
        }
    }
}
