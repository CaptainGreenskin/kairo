/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.diagnostics;

import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.Range;

/**
 * Shift the line number of a baseline diagnostic forward/backward by the net line delta of an edit,
 * so the baseline can be compared against the post-edit diagnostics without false positives on
 * shifted lines.
 *
 * <p>Model: an edit is characterized by {@code editStartLine} (0-based, inclusive) and a {@code
 * lineDelta} (positive when lines were added, negative when removed). Diagnostics that start
 * <em>before</em> the edit are unaffected; diagnostics that start at or after the edit get {@code
 * lineDelta} added to both range endpoints. Diagnostics that fell inside a removed region collapse
 * to the edit start.
 */
public final class RangeShift {

    private RangeShift() {}

    public static Diagnostic shift(Diagnostic d, int editStartLine, int lineDelta) {
        if (lineDelta == 0) return d;
        Range r = d.range();
        if (r.startLine() < editStartLine) return d;
        int newStart = Math.max(editStartLine, r.startLine() + lineDelta);
        int newEnd = Math.max(newStart, r.endLine() + lineDelta);
        Range shifted = new Range(newStart, r.startCharacter(), newEnd, r.endCharacter());
        return new Diagnostic(d.uri(), shifted, d.severity(), d.message(), d.code(), d.source());
    }

    /**
     * Compute the line delta between the {@code before} and {@code after} contents — naive but
     * matches the "I overwrote the whole file" pattern used by WriteTool / patch. Callers with more
     * nuanced edit ops should compute their own delta + editStartLine.
     */
    public static EditShape forWholeFileReplace(String before, String after) {
        int beforeLines = countLines(before);
        int afterLines = countLines(after);
        return new EditShape(0, afterLines - beforeLines);
    }

    private static int countLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') count++;
        return count;
    }

    public record EditShape(int editStartLine, int lineDelta) {}
}
