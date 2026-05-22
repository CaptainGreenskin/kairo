/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.DiagnosticSeverity;
import io.kairo.api.lsp.Range;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosticsBaselineTest {

    private final DiagnosticsBaseline baselines = new DiagnosticsBaseline();
    private static final String URI = "file:///foo.py";

    private static Diagnostic at(int line, String msg) {
        return new Diagnostic(
                URI, new Range(line, 0, line, 5), DiagnosticSeverity.ERROR, msg, null, null);
    }

    @Test
    void noBaselineMeansEverythingIsNew() {
        var diff =
                baselines.diffSinceLastSnapshot(
                        URI, List.of(at(1, "a"), at(2, "b")), new RangeShift.EditShape(0, 0));
        assertThat(diff).hasSize(2);
    }

    @Test
    void identicalSetYieldsNoNewDiagnostics() {
        baselines.snapshot(URI, List.of(at(1, "a"), at(2, "b")));
        var diff =
                baselines.diffSinceLastSnapshot(
                        URI, List.of(at(1, "a"), at(2, "b")), new RangeShift.EditShape(0, 0));
        assertThat(diff).isEmpty();
    }

    @Test
    void onlyNovelEntriesAreReturned() {
        baselines.snapshot(URI, List.of(at(1, "a")));
        var diff =
                baselines.diffSinceLastSnapshot(
                        URI, List.of(at(1, "a"), at(7, "c")), new RangeShift.EditShape(0, 0));
        assertThat(diff).extracting(Diagnostic::message).containsExactly("c");
    }

    @Test
    void baselineIsShiftedBeforeComparing() {
        baselines.snapshot(URI, List.of(at(5, "old")));
        // Edit pushed line 5 down to line 8 (delta=3 starting at line 0)
        var diff =
                baselines.diffSinceLastSnapshot(
                        URI, List.of(at(8, "old")), new RangeShift.EditShape(0, 3));
        assertThat(diff).isEmpty();
    }

    @Test
    void snapshotReplacesPreviousBaseline() {
        baselines.snapshot(URI, List.of(at(1, "a")));
        baselines.snapshot(URI, List.of(at(2, "b")));
        assertThat(baselines.baseline(URI)).extracting(Diagnostic::message).containsExactly("b");
    }

    @Test
    void forgetClearsBaseline() {
        baselines.snapshot(URI, List.of(at(1, "a")));
        baselines.forget(URI);
        assertThat(baselines.hasBaseline(URI)).isFalse();
    }
}
