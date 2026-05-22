/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.lsp;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolved (server, workspace-root) pair — the pool key for an {@link LspService}. Two edits in the
 * same project that hit the same server share one LSP subprocess; two projects each get their own.
 */
public record WorkspaceRoot(String serverId, Path root) {

    public WorkspaceRoot {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(root, "root");
        root = root.toAbsolutePath().normalize();
    }
}
