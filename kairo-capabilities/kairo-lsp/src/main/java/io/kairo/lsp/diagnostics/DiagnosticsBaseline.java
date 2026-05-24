/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.diagnostics;

import io.kairo.api.lsp.Diagnostic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-uri baseline diagnostics store. Tool implementations call {@link #snapshot} before the edit;
 * after the edit + new diagnostics arrive, {@link #diffSinceLastSnapshot} returns only the
 * diagnostics absent from the baseline (after applying the edit's line shift).
 *
 * <p>The baseline is replaced each time {@link #snapshot} is called for a uri, so the same store
 * can be reused across multiple sequential edits to the same file.
 */
public final class DiagnosticsBaseline {

    private final Map<String, List<Diagnostic>> baselineByUri = new ConcurrentHashMap<>();

    /** Replace the baseline for {@code uri}. */
    public void snapshot(String uri, List<Diagnostic> current) {
        baselineByUri.put(uri, current == null ? List.of() : List.copyOf(current));
    }

    /** Forget any baseline for this uri. */
    public void forget(String uri) {
        baselineByUri.remove(uri);
    }

    public boolean hasBaseline(String uri) {
        return baselineByUri.containsKey(uri);
    }

    public List<Diagnostic> baseline(String uri) {
        return baselineByUri.getOrDefault(uri, List.of());
    }

    /**
     * Return diagnostics from {@code newDiagnostics} whose fingerprint is not present in the
     * baseline (after shifting baseline diagnostics by {@code edit}). If there is no baseline, the
     * entire {@code newDiagnostics} list is returned.
     */
    public List<Diagnostic> diffSinceLastSnapshot(
            String uri, List<Diagnostic> newDiagnostics, RangeShift.EditShape edit) {
        if (newDiagnostics == null || newDiagnostics.isEmpty()) return List.of();
        List<Diagnostic> baseline = baselineByUri.get(uri);
        if (baseline == null) return List.copyOf(newDiagnostics);

        Set<String> baselineFps = new HashSet<>();
        for (Diagnostic b : baseline) {
            Diagnostic shifted = RangeShift.shift(b, edit.editStartLine(), edit.lineDelta());
            baselineFps.add(shifted.fingerprint());
        }
        List<Diagnostic> introduced = new ArrayList<>();
        for (Diagnostic d : newDiagnostics) {
            if (!baselineFps.contains(d.fingerprint())) introduced.add(d);
        }
        return List.copyOf(introduced);
    }

    public int knownUriCount() {
        return baselineByUri.size();
    }
}
