/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.lsp;

/**
 * Half-open character range in a single document. Lines and characters are 0-based, matching the
 * LSP wire format. {@code endLine}/{@code endCharacter} are exclusive.
 */
public record Range(int startLine, int startCharacter, int endLine, int endCharacter) {

    public Range {
        if (startLine < 0 || startCharacter < 0 || endLine < 0 || endCharacter < 0) {
            throw new IllegalArgumentException("Range coordinates must be non-negative");
        }
        if (endLine < startLine || (endLine == startLine && endCharacter < startCharacter)) {
            throw new IllegalArgumentException("Range end must not precede start");
        }
    }

    public static Range singleLine(int line, int startChar, int endChar) {
        return new Range(line, startChar, line, endChar);
    }
}
