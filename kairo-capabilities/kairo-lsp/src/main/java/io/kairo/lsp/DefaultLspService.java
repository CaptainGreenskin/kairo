/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp;

import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.LanguageServerRegistry;
import io.kairo.api.lsp.LspException;
import io.kairo.api.lsp.LspService;
import io.kairo.api.lsp.ServerDef;
import io.kairo.api.lsp.SymbolInfo;
import io.kairo.lsp.client.LspClient;
import io.kairo.lsp.client.LspClientPool;
import io.kairo.lsp.diagnostics.DiagnosticsBaseline;
import io.kairo.lsp.diagnostics.RangeShift;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default {@link LspService} implementation, wiring the {@link LanguageServerRegistry}, the {@link
 * LspClientPool}, and the {@link DiagnosticsBaseline} together.
 *
 * <p>{@code enabledFor} delegates to a {@link Predicate}; the default refuses to run unless the
 * file lives under a directory that contains {@code .git} (so a home-dir chat won't fork daemons).
 * Override via {@link Builder#enabledPredicate(Predicate)}.
 */
public final class DefaultLspService implements LspService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLspService.class);

    private final LanguageServerRegistry registry;
    private final LspClientPool pool;
    private final DiagnosticsBaseline baselines = new DiagnosticsBaseline();
    private final Predicate<Path> enabledPredicate;
    private final Duration diagnosticsTimeout;
    private final Map<String, String> lastContentByUri = new ConcurrentHashMap<>();
    private final Map<String, String> baselineContentByUri = new ConcurrentHashMap<>();

    private DefaultLspService(Builder b) {
        this.registry = Objects.requireNonNull(b.registry, "registry");
        this.pool = Objects.requireNonNullElseGet(b.pool, LspClientPool::new);
        this.enabledPredicate =
                Objects.requireNonNullElse(
                        b.enabledPredicate, DefaultLspService::insideGitWorktree);
        this.diagnosticsTimeout =
                Objects.requireNonNullElse(b.diagnosticsTimeout, Duration.ofSeconds(3));
    }

    public static Builder builder(LanguageServerRegistry registry) {
        return new Builder(registry);
    }

    @Override
    public boolean enabledFor(Path filePath) {
        if (filePath == null) return false;
        if (registry.findFor(filePath).isEmpty()) return false;
        return enabledPredicate.test(filePath);
    }

    @Override
    public Mono<List<Diagnostic>> snapshotBaseline(Path filePath) {
        return Mono.fromCallable(
                        () -> {
                            ResolvedClient r = resolve(filePath);
                            if (r == null) return List.<Diagnostic>of();
                            String content = readQuietly(filePath);
                            r.client.openOrUpdate(filePath, content);
                            List<Diagnostic> current =
                                    r.client.waitForDiagnostics(filePath, diagnosticsTimeout);
                            baselines.snapshot(uri(filePath), current);
                            baselineContentByUri.put(uri(filePath), content);
                            lastContentByUri.put(uri(filePath), content);
                            return current;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> notifyChange(Path filePath, String newContent) {
        return Mono.<Void>fromRunnable(
                        () -> {
                            ResolvedClient r = resolve(filePath);
                            if (r == null) return;
                            r.client.openOrUpdate(filePath, newContent);
                            lastContentByUri.put(uri(filePath), newContent);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<Diagnostic>> diagnosticsSince(Path filePath, Duration timeout) {
        return Mono.fromCallable(
                        () -> {
                            ResolvedClient r = resolve(filePath);
                            if (r == null) return List.<Diagnostic>of();
                            List<Diagnostic> latest =
                                    r.client.waitForDiagnostics(
                                            filePath,
                                            timeout == null ? diagnosticsTimeout : timeout);
                            String uri = uri(filePath);
                            String before = baselineContentByUri.get(uri);
                            String after = lastContentByUri.get(uri);
                            RangeShift.EditShape shape =
                                    before == null || after == null
                                            ? new RangeShift.EditShape(0, 0)
                                            : RangeShift.forWholeFileReplace(before, after);
                            return baselines.diffSinceLastSnapshot(uri, latest, shape);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<Diagnostic>> currentDiagnostics(Path filePath) {
        return Mono.fromCallable(
                        () -> {
                            ResolvedClient r = resolve(filePath);
                            if (r == null) return List.<Diagnostic>of();
                            return r.client.currentDiagnostics(filePath);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<SymbolInfo>> searchSymbols(Path workspaceRoot, String query, int limit) {
        return Mono.fromCallable(
                        () -> {
                            List<SymbolInfo> all = new java.util.ArrayList<>();
                            for (var def : registry.all()) {
                                var client = pool.acquire(def, workspaceRoot);
                                if (client != null) {
                                    all.addAll(client.workspaceSymbol(query, limit - all.size()));
                                    if (all.size() >= limit) break;
                                }
                            }
                            return all.size() > limit ? all.subList(0, limit) : all;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.<Void>fromRunnable(pool::close).subscribeOn(Schedulers.boundedElastic());
    }

    public LanguageServerRegistry registry() {
        return registry;
    }

    public LspClientPool pool() {
        return pool;
    }

    public DiagnosticsBaseline baselines() {
        return baselines;
    }

    private ResolvedClient resolve(Path filePath) {
        Optional<ServerDef> defOpt = registry.findFor(filePath);
        if (defOpt.isEmpty()) return null;
        ServerDef def = defOpt.get();
        Path root = registry.resolveWorkspaceRoot(filePath, def);
        LspClient client = pool.acquire(def, root);
        if (client == null) return null;
        return new ResolvedClient(def, client);
    }

    private static String uri(Path filePath) {
        return filePath.toAbsolutePath().normalize().toUri().toString();
    }

    private static String readQuietly(Path filePath) {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            return "";
        }
    }

    /** Default {@code enabledFor}: walk up from the file looking for a {@code .git} marker. */
    public static boolean insideGitWorktree(Path filePath) {
        if (filePath == null) return false;
        Path cursor =
                filePath.toAbsolutePath().normalize().getParent() == null
                        ? filePath.toAbsolutePath().normalize()
                        : filePath.toAbsolutePath().normalize().getParent();
        while (cursor != null) {
            if (Files.exists(cursor.resolve(".git"))) return true;
            cursor = cursor.getParent();
        }
        return false;
    }

    private record ResolvedClient(ServerDef def, LspClient client) {}

    /** Builder for the default service. */
    public static final class Builder {
        private final LanguageServerRegistry registry;
        private LspClientPool pool;
        private Predicate<Path> enabledPredicate;
        private Duration diagnosticsTimeout;

        private Builder(LanguageServerRegistry registry) {
            this.registry = registry;
        }

        public Builder pool(LspClientPool pool) {
            this.pool = pool;
            return this;
        }

        public Builder enabledPredicate(Predicate<Path> p) {
            this.enabledPredicate = p;
            return this;
        }

        public Builder diagnosticsTimeout(Duration d) {
            this.diagnosticsTimeout = d;
            return this;
        }

        public DefaultLspService build() {
            try {
                return new DefaultLspService(this);
            } catch (Exception e) {
                throw new LspException("Failed to build DefaultLspService", e);
            }
        }
    }
}
