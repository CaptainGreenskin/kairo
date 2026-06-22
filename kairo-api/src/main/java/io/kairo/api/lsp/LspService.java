/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.lsp;

import io.kairo.api.Experimental;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Internal-infrastructure entry point for LSP diagnostics. NOT exposed to the model as a tool —
 * agents do not call {@code findDefinition} / {@code rename}. The single purpose is to surface
 * post-edit diagnostics so {@link io.kairo.api.tool.ToolExecutor} implementations can include "did
 * this edit introduce new errors?" in their result.
 *
 * <p>Lifecycle: {@link #snapshotBaseline(Path)} before an edit, perform the edit + {@link
 * #notifyChange(Path, String)}, then {@link #diagnosticsSince(Path, Duration)} returns the diff.
 * Repeated calls reuse the same subprocess.
 *
 * @since 1.3 (Experimental)
 */
@Experimental("LSP SPI — contract may change in v1.x")
public interface LspService {

    /**
     * Whether this service is willing to run for {@code filePath}'s workspace. Hosts typically gate
     * on "inside a git worktree" so a home-directory chat does not spawn daemons.
     */
    boolean enabledFor(Path filePath);

    /**
     * Snapshot the current diagnostics for the file's workspace as the baseline. Caller invokes
     * before the edit. Returns the baseline diagnostics list (for inspection).
     */
    Mono<List<Diagnostic>> snapshotBaseline(Path filePath);

    /**
     * Notify the LSP server that {@code filePath} now has {@code newContent}. Triggers an LSP
     * {@code textDocument/didChange} after opening the document if necessary.
     */
    Mono<Void> notifyChange(Path filePath, String newContent);

    /**
     * Wait up to {@code timeout} for new diagnostics relative to the most recent baseline for this
     * file's workspace, then return only diagnostics absent from that baseline (after adjusting
     * their line numbers for the edit just applied).
     */
    Mono<List<Diagnostic>> diagnosticsSince(Path filePath, Duration timeout);

    /** Current full diagnostics for {@code filePath}, no baseline diff applied. */
    Mono<List<Diagnostic>> currentDiagnostics(Path filePath);

    /**
     * Search for workspace symbols matching the given query via {@code workspace/symbol}. Returns
     * an empty list when the language server does not support symbol search.
     *
     * @param workspaceRoot the workspace root directory
     * @param query the symbol search query (fuzzy matched by the language server)
     * @param limit maximum number of results to return
     * @since 1.3
     */
    default Mono<List<SymbolInfo>> searchSymbols(Path workspaceRoot, String query, int limit) {
        return Mono.just(List.of());
    }

    /** Stop all spawned subprocesses and release resources. Idempotent. */
    Mono<Void> shutdown();
}
