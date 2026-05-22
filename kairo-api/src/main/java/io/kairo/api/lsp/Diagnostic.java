/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.lsp;

import io.kairo.api.Experimental;
import java.util.Objects;

/**
 * A single diagnostic surfaced by a language server.
 *
 * <p>{@code uri} is the {@code file://}-style identifier the LSP server emits. {@code code} and
 * {@code source} may be null when the server omits them.
 *
 * @since 1.3 (Experimental)
 */
@Experimental("LSP SPI — contract may change in v1.x")
public record Diagnostic(
        String uri,
        Range range,
        DiagnosticSeverity severity,
        String message,
        String code,
        String source) {

    public Diagnostic {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    /** Identity used for baseline diffing — uri + range + severity + message. */
    public String fingerprint() {
        return uri
                + '|'
                + range.startLine()
                + ':'
                + range.startCharacter()
                + '|'
                + severity
                + '|'
                + message;
    }
}
