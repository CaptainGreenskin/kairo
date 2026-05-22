/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.lsp;

import io.kairo.api.Experimental;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Registry of {@link ServerDef}s. Routes a file path to the language server (if any) that should
 * own its diagnostics, and resolves the workspace root for that server.
 *
 * @since 1.3 (Experimental)
 */
@Experimental("LSP SPI — contract may change in v1.x")
public interface LanguageServerRegistry {

    /** Register a server definition. Last write wins on serverId collision. */
    void register(ServerDef def);

    /** All registered server definitions, snapshot. */
    List<ServerDef> all();

    Optional<ServerDef> findById(String serverId);

    /**
     * Resolve the server that should handle {@code filePath}. Walks the file's directory looking at
     * registered {@code supportedExtensions}; the first match wins (registration order).
     */
    Optional<ServerDef> findFor(Path filePath);

    /**
     * Walk upward from {@code filePath} looking for any of {@code def}'s {@code rootMarkers}. Falls
     * back to the file's parent directory if no marker is found.
     */
    Path resolveWorkspaceRoot(Path filePath, ServerDef def);
}
