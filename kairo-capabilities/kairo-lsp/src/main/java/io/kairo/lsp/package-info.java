/*
 * Copyright 2025-2026 the Kairo authors.
 */
/**
 * Default implementation of the {@link io.kairo.api.lsp Kairo LSP SPI}. Spawns real language
 * servers as subprocesses, pools them by {@code (server, workspace-root)}, and exposes a
 * baseline-diff view so tool implementations can attach "did this edit introduce new errors?" to
 * their result.
 *
 * @since 1.3 (Experimental)
 */
package io.kairo.lsp;
