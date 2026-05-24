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

import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.LspService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for the kairo-tools file writers (Write / Edit / PatchApply / SearchReplace / BatchWrite).
 * Wraps a write operation with an LSP baseline-diff so the resulting tool result can carry "this
 * edit introduced N new diagnostics" feedback without each tool re-implementing the pre-snapshot /
 * post-wait / diff dance.
 *
 * <p>The hook is a no-op when {@code lspService} is null OR when {@link LspService#enabledFor}
 * returns false for the target path. This is intentional: most kairo deployments will not opt into
 * LSP, and the file tools must keep working with zero overhead and zero behavior change in that
 * case.
 *
 * <p>Failures inside the LSP path (timeouts, spawn errors, IO errors) are swallowed with a
 * debug-level log. The write itself is authoritative; diagnostics are a nice-to-have signal.
 */
public final class PostEditDiagnosticsHook {

    private static final Logger log = LoggerFactory.getLogger(PostEditDiagnosticsHook.class);
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(3);

    private final LspService lspService;
    private final Duration waitTimeout;

    public PostEditDiagnosticsHook(LspService lspService) {
        this(lspService, DEFAULT_WAIT);
    }

    public PostEditDiagnosticsHook(LspService lspService, Duration waitTimeout) {
        this.lspService = lspService;
        this.waitTimeout = waitTimeout == null ? DEFAULT_WAIT : waitTimeout;
    }

    /** True when the hook would actually do something for this path. */
    public boolean isActiveFor(Path filePath) {
        return lspService != null && filePath != null && lspService.enabledFor(filePath);
    }

    /**
     * Capture a baseline before the edit. Returns a token to pass to {@link #afterWrite(Token,
     * String)} once the edit is committed. Returns null when LSP is inactive — callers can pass
     * null straight through to afterWrite without branching.
     */
    public Token beforeWrite(Path filePath) {
        if (!isActiveFor(filePath)) return null;
        try {
            lspService.snapshotBaseline(filePath).block(waitTimeout);
        } catch (Exception e) {
            log.debug("LSP baseline snapshot failed for {}: {}", filePath, e.getMessage());
            return null;
        }
        return new Token(filePath);
    }

    /**
     * After the write, notify LSP of the new content and return only diagnostics that were absent
     * from the baseline (with edit line-shift applied). Returns an empty list when LSP is inactive
     * or anything goes wrong — never throws.
     */
    public List<Diagnostic> afterWrite(Token token, String newContent) {
        if (token == null) return List.of();
        try {
            lspService.notifyChange(token.filePath, newContent).block(waitTimeout);
            List<Diagnostic> introduced =
                    lspService.diagnosticsSince(token.filePath, waitTimeout).block(waitTimeout);
            return introduced == null ? List.of() : introduced;
        } catch (Exception e) {
            log.debug("LSP diagnostics fetch failed for {}: {}", token.filePath, e.getMessage());
            return List.of();
        }
    }

    /**
     * Convenience for tools that already hold the new content as a String and the file as a Path,
     * with no separate read step needed. Returns the diagnostics list (possibly empty).
     */
    public List<Diagnostic> runAround(Path filePath, String newContent) {
        Token t = beforeWrite(filePath);
        return afterWrite(t, newContent);
    }

    /**
     * Read the current file content for use as the post-edit notification body. Returns "" when the
     * file does not exist or the read fails.
     */
    public static String readQuietly(Path filePath) {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Render diagnostics as a metadata-friendly list of maps (uri, line, severity, message, code,
     * source). Returns empty list when input is empty so callers can always include the key.
     */
    public static List<Map<String, Object>> toMetadata(List<Diagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) return List.of();
        return diagnostics.stream()
                .map(
                        d -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("uri", d.uri());
                            m.put("line", d.range().startLine());
                            m.put("character", d.range().startCharacter());
                            m.put("severity", d.severity().name());
                            m.put("message", d.message());
                            if (d.code() != null) m.put("code", d.code());
                            if (d.source() != null) m.put("source", d.source());
                            return m;
                        })
                .toList();
    }

    /** Opaque cookie returned by beforeWrite and consumed by afterWrite. */
    public static final class Token {
        private final Path filePath;

        private Token(Path filePath) {
            this.filePath = filePath;
        }

        public Path filePath() {
            return filePath;
        }
    }
}
