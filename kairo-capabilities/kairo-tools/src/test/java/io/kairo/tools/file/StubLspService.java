/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.tools.file;

import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.LspService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

/**
 * Predictable LspService stand-in for tool tests. Each test path can have a "diagnostics queue"
 * that {@link #diagnosticsSince} drains one entry at a time. {@link #snapshotBaseline} records the
 * baseline call. No subprocesses, no I/O.
 */
final class StubLspService implements LspService {

    private final Map<String, List<List<Diagnostic>>> queues = new ConcurrentHashMap<>();
    private final List<Path> snapshots = new ArrayList<>();
    private final List<Path> changes = new ArrayList<>();
    private boolean enabled = true;

    static StubLspService disabled() {
        StubLspService s = new StubLspService();
        s.enabled = false;
        return s;
    }

    void enqueueDiagnostics(Path filePath, List<Diagnostic> next) {
        queues.computeIfAbsent(key(filePath), k -> new ArrayList<>()).add(next);
    }

    List<Path> snapshotedPaths() {
        return List.copyOf(snapshots);
    }

    List<Path> changedPaths() {
        return List.copyOf(changes);
    }

    @Override
    public boolean enabledFor(Path filePath) {
        return enabled;
    }

    @Override
    public Mono<List<Diagnostic>> snapshotBaseline(Path filePath) {
        snapshots.add(filePath);
        return Mono.just(List.of());
    }

    @Override
    public Mono<Void> notifyChange(Path filePath, String newContent) {
        changes.add(filePath);
        return Mono.empty();
    }

    @Override
    public Mono<List<Diagnostic>> diagnosticsSince(Path filePath, Duration timeout) {
        List<List<Diagnostic>> q = queues.get(key(filePath));
        if (q == null || q.isEmpty()) return Mono.just(List.of());
        return Mono.just(q.remove(0));
    }

    @Override
    public Mono<List<Diagnostic>> currentDiagnostics(Path filePath) {
        return Mono.just(List.of());
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.empty();
    }

    private static String key(Path p) {
        return p.toAbsolutePath().normalize().toString();
    }
}
