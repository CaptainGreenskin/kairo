/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.lsp;

import io.kairo.api.Experimental;

/**
 * A workspace symbol returned by {@link LspService#searchSymbols}.
 *
 * @param name the symbol name (class, method, field, etc.)
 * @param kind the symbol kind (e.g. "Class", "Interface", "Method", "Function")
 * @param file the file path relative to workspace root
 * @param line the 1-based line number where the symbol is defined
 * @param containerName the containing class/module, or null for top-level symbols
 * @since 1.3
 */
@Experimental("LSP symbol info — contract may change")
public record SymbolInfo(String name, String kind, String file, int line, String containerName) {}
