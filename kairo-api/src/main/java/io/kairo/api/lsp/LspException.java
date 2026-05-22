/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.lsp;

/** Unchecked failure raised by the LSP subsystem. */
public class LspException extends RuntimeException {

    public LspException(String message) {
        super(message);
    }

    public LspException(String message, Throwable cause) {
        super(message, cause);
    }
}
