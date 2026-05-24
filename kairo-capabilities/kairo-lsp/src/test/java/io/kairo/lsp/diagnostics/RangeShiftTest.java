/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.DiagnosticSeverity;
import io.kairo.api.lsp.Range;
import org.junit.jupiter.api.Test;

class RangeShiftTest {

    private static Diagnostic at(int startLine) {
        return new Diagnostic(
                "file:///x.py",
                new Range(startLine, 0, startLine, 5),
                DiagnosticSeverity.ERROR,
                "msg",
                null,
                null);
    }

    @Test
    void noShiftReturnsSameInstance() {
        Diagnostic d = at(10);
        assertThat(RangeShift.shift(d, 0, 0)).isSameAs(d);
    }

    @Test
    void diagnosticBeforeEditUnshifted() {
        Diagnostic d = at(2);
        Diagnostic shifted = RangeShift.shift(d, 10, 5);
        assertThat(shifted.range().startLine()).isEqualTo(2);
    }

    @Test
    void diagnosticAfterEditShiftedDown() {
        Diagnostic d = at(20);
        Diagnostic shifted = RangeShift.shift(d, 10, 5);
        assertThat(shifted.range().startLine()).isEqualTo(25);
        assertThat(shifted.range().endLine()).isEqualTo(25);
    }

    @Test
    void diagnosticAfterEditShiftedUp() {
        Diagnostic d = at(20);
        Diagnostic shifted = RangeShift.shift(d, 10, -3);
        assertThat(shifted.range().startLine()).isEqualTo(17);
    }

    @Test
    void diagnosticInsideRemovedRegionCollapsesToEditStart() {
        Diagnostic d = at(11);
        Diagnostic shifted = RangeShift.shift(d, 10, -5);
        assertThat(shifted.range().startLine()).isEqualTo(10);
    }

    @Test
    void wholeFileReplaceDeltaPositiveForAddedLines() {
        var shape = RangeShift.forWholeFileReplace("a\nb\n", "a\nb\nc\n");
        assertThat(shape.editStartLine()).isEqualTo(0);
        assertThat(shape.lineDelta()).isEqualTo(1);
    }

    @Test
    void wholeFileReplaceDeltaNegativeForRemovedLines() {
        var shape = RangeShift.forWholeFileReplace("a\nb\nc\n", "a\n");
        assertThat(shape.lineDelta()).isEqualTo(-2);
    }

    @Test
    void emptyContentCountsAsZeroLines() {
        var shape = RangeShift.forWholeFileReplace("", "a\nb\n");
        assertThat(shape.lineDelta()).isEqualTo(3);
    }
}
