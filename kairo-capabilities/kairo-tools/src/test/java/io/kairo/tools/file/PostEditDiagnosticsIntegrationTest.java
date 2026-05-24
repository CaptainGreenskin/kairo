/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.tools.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.DiagnosticSeverity;
import io.kairo.api.lsp.Range;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.workspace.WorkspaceRequest;
import io.kairo.core.workspace.LocalDirectoryWorkspaceProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the five file-write tools attach {@code newDiagnostics} metadata when an LSP service is
 * wired and reports diagnostics after the edit. Uses {@link StubLspService} — no subprocesses.
 */
class PostEditDiagnosticsIntegrationTest {

    private ToolContext ctxFor(Path workspaceRoot) {
        var ws =
                new LocalDirectoryWorkspaceProvider(workspaceRoot)
                        .acquire(WorkspaceRequest.writable(null));
        return new ToolContext("a", "s", Map.of(), null, null, ws);
    }

    private Diagnostic syntaxError(Path file) {
        return new Diagnostic(
                file.toAbsolutePath().normalize().toUri().toString(),
                new Range(0, 0, 0, 5),
                DiagnosticSeverity.ERROR,
                "syntax error",
                null,
                "test-lsp");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writeToolAttachesNewDiagnostics(@TempDir Path tmp) {
        Path file = tmp.resolve("a.py");
        StubLspService lsp = new StubLspService();
        lsp.enqueueDiagnostics(file, List.of(syntaxError(file)));
        WriteTool tool = new WriteTool(null, lsp);

        ToolResult r =
                tool.execute(Map.of("path", file.toString(), "content", "print(\n"), ctxFor(tmp))
                        .block();

        assertThat(r).isNotNull();
        assertThat(r.isError()).isFalse();
        var meta = (Map<String, Object>) r.metadata();
        assertThat(meta).containsKey("newDiagnostics");
        var diags = (List<Map<String, Object>>) meta.get("newDiagnostics");
        assertThat(diags).hasSize(1);
        assertThat(diags.get(0)).containsEntry("message", "syntax error");
        assertThat(diags.get(0)).containsEntry("severity", "ERROR");
        assertThat(lsp.snapshotedPaths()).hasSize(1);
        assertThat(lsp.changedPaths()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writeToolWithoutLspHasNoDiagnosticsKey(@TempDir Path tmp) {
        Path file = tmp.resolve("a.py");
        WriteTool tool = new WriteTool();
        ToolResult r =
                tool.execute(Map.of("path", file.toString(), "content", "x = 1\n"), ctxFor(tmp))
                        .block();
        assertThat(r).isNotNull();
        assertThat(r.isError()).isFalse();
        var meta = (Map<String, Object>) r.metadata();
        assertThat(meta).doesNotContainKey("newDiagnostics");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writeToolWithDisabledLspIsNoOp(@TempDir Path tmp) {
        Path file = tmp.resolve("a.py");
        WriteTool tool = new WriteTool(null, StubLspService.disabled());
        ToolResult r =
                tool.execute(Map.of("path", file.toString(), "content", "x = 1\n"), ctxFor(tmp))
                        .block();
        assertThat(r).isNotNull();
        assertThat(r.isError()).isFalse();
        var meta = (Map<String, Object>) r.metadata();
        assertThat(meta).doesNotContainKey("newDiagnostics");
    }

    @Test
    @SuppressWarnings("unchecked")
    void editToolAttachesNewDiagnostics(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("b.py");
        Files.writeString(file, "foo()\n");
        StubLspService lsp = new StubLspService();
        lsp.enqueueDiagnostics(file, List.of(syntaxError(file)));
        EditTool tool = new EditTool(null, lsp);

        ToolResult r =
                tool.execute(
                                Map.of(
                                        "path", file.toString(),
                                        "originalText", "foo",
                                        "newText", "bar"),
                                ctxFor(tmp))
                        .block();

        assertThat(r).isNotNull();
        assertThat(r.isError()).isFalse();
        var meta = (Map<String, Object>) r.metadata();
        assertThat(meta).containsKey("newDiagnostics");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchReplaceToolAttachesNewDiagnostics(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("c.py");
        Files.writeString(file, "alpha beta\n");
        StubLspService lsp = new StubLspService();
        lsp.enqueueDiagnostics(file, List.of(syntaxError(file)));
        SearchReplaceTool tool = new SearchReplaceTool(lsp);

        ToolResult r =
                tool.execute(
                                Map.of(
                                        "path", file.toString(),
                                        "search", "alpha",
                                        "replace", "GAMMA"),
                                ctxFor(tmp))
                        .block();

        assertThat(r).isNotNull();
        assertThat(r.isError()).isFalse();
        var meta = (Map<String, Object>) r.metadata();
        assertThat(meta).containsKey("newDiagnostics");
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchWriteAttachesPerFileNewDiagnostics(@TempDir Path tmp) {
        Path fileA = tmp.resolve("d.py");
        Path fileB = tmp.resolve("e.py");
        StubLspService lsp = new StubLspService();
        lsp.enqueueDiagnostics(fileA, List.of(syntaxError(fileA)));
        // fileB has no diagnostics
        BatchWriteTool tool = new BatchWriteTool(lsp);

        ToolResult r =
                tool.execute(
                                Map.of(
                                        "files",
                                        List.of(
                                                Map.of("path", "d.py", "content", "x = 1\n"),
                                                Map.of("path", "e.py", "content", "y = 2\n"))),
                                ctxFor(tmp))
                        .block();

        assertThat(r).isNotNull();
        assertThat(r.isError()).isFalse();
        var meta = (Map<String, Object>) r.metadata();
        assertThat(meta).containsKey("newDiagnostics");
        var diagsByPath = (Map<String, Object>) meta.get("newDiagnostics");
        assertThat(diagsByPath).containsOnlyKeys("d.py");
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyDiagnosticsDoesNotPolluteMetadata(@TempDir Path tmp) {
        Path file = tmp.resolve("clean.py");
        StubLspService lsp = new StubLspService();
        // no enqueued diagnostics — returns empty list
        WriteTool tool = new WriteTool(null, lsp);

        ToolResult r =
                tool.execute(Map.of("path", file.toString(), "content", "x = 1\n"), ctxFor(tmp))
                        .block();

        var meta = (Map<String, Object>) r.metadata();
        assertThat(meta).doesNotContainKey("newDiagnostics");
    }
}
