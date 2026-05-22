/*
 * Copyright 2025-2026 the Kairo authors.
 */
/**
 * SPI for the Kairo LSP diagnostics subsystem.
 *
 * <p>This package is the contract surface used by {@code kairo-lsp} (the default implementation)
 * and by tool implementations that want to incorporate post-edit diagnostics into their result.
 *
 * <p>The LSP subsystem is internal infrastructure: it spawns real language servers as subprocesses
 * to compute diagnostics for files the agent just edited. It is NOT a model-facing tool surface —
 * the agent never calls "find_definition" / "rename" / etc. The diagnostics flow stays inside the
 * framework so a {@code write_file} result can include "the edit you just made introduced these N
 * new errors" without inventing a separate tool.
 *
 * @since 1.3 (Experimental)
 */
package io.kairo.api.lsp;
