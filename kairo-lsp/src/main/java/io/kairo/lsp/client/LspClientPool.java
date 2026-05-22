/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.client;

import io.kairo.api.lsp.LspException;
import io.kairo.api.lsp.ServerDef;
import io.kairo.api.lsp.WorkspaceRoot;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pool of {@link LspClient}s keyed by {@code (serverId, workspaceRoot)}. Two edits in the same
 * project share one subprocess; two projects each get their own.
 *
 * <p>Two short-circuits prevent the agent from stalling on a misconfigured server:
 *
 * <ul>
 *   <li><b>Broken set</b> — when {@link LspClient#start()} throws, the key goes into {@code
 *       brokenKeys} and {@link #acquire(ServerDef, Path)} returns null forever after for that key.
 *       Restart requires explicit {@link #forget(WorkspaceRoot)}.
 *   <li><b>Idle reaper</b> — a background task closes clients that haven't been touched in {@code
 *       idleTimeout}; the next use re-spawns a fresh subprocess (unless broken).
 * </ul>
 */
public final class LspClientPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LspClientPool.class);

    private final BiFunction<ServerDef, Path, LspClient> clientFactory;
    private final Duration idleTimeout;
    private final Map<WorkspaceRoot, Entry> entries = new ConcurrentHashMap<>();
    private final Set<WorkspaceRoot> brokenKeys = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService reaper;

    public LspClientPool() {
        this(LspClient::new, Duration.ofMinutes(10));
    }

    public LspClientPool(
            BiFunction<ServerDef, Path, LspClient> clientFactory, Duration idleTimeout) {
        this.clientFactory = clientFactory;
        this.idleTimeout = idleTimeout;
        this.reaper =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "lsp-pool-reaper");
                            t.setDaemon(true);
                            return t;
                        });
        long period = Math.max(60_000, idleTimeout.toMillis() / 4);
        this.reaper.scheduleAtFixedRate(this::reapIdle, period, period, TimeUnit.MILLISECONDS);
    }

    /**
     * Return the client for {@code (def, root)}, starting it lazily. Returns null if the key is in
     * the broken set (caller should treat this as "no diagnostics available, skip silently").
     */
    public LspClient acquire(ServerDef def, Path root) {
        WorkspaceRoot key = new WorkspaceRoot(def.serverId(), root);
        if (brokenKeys.contains(key)) return null;
        Entry e = entries.computeIfAbsent(key, k -> new Entry(clientFactory.apply(def, k.root())));
        e.touch();
        synchronized (e) {
            if (!e.client.isRunning() && !e.client.isBroken()) {
                try {
                    e.client.start();
                } catch (LspException ex) {
                    log.warn(
                            "LSP server {} failed to start for {} — marking key broken: {}",
                            def.serverId(),
                            root,
                            ex.getMessage());
                    brokenKeys.add(key);
                    entries.remove(key);
                    return null;
                }
            }
            if (e.client.isBroken()) {
                brokenKeys.add(key);
                entries.remove(key);
                return null;
            }
        }
        return e.client;
    }

    /**
     * Remove the key from the broken set so the next {@link #acquire(ServerDef, Path)} retries
     * spawning. Use after the user fixes their PATH / installs the binary.
     */
    public void forget(WorkspaceRoot key) {
        brokenKeys.remove(key);
        Entry e = entries.remove(key);
        if (e != null) e.client.shutdown();
    }

    public Set<WorkspaceRoot> brokenKeys() {
        return Set.copyOf(brokenKeys);
    }

    public Set<WorkspaceRoot> activeKeys() {
        return Set.copyOf(entries.keySet());
    }

    private void reapIdle() {
        Instant cutoff = Instant.now().minus(idleTimeout);
        for (var iter = entries.entrySet().iterator(); iter.hasNext(); ) {
            var entry = iter.next();
            Entry e = entry.getValue();
            if (e.lastUsed.isBefore(cutoff)) {
                log.debug("LSP pool reaping idle client for {}", entry.getKey());
                try {
                    e.client.shutdown();
                } catch (Exception ignore) {
                }
                iter.remove();
            }
        }
    }

    @Override
    public void close() {
        reaper.shutdownNow();
        for (Entry e : entries.values()) {
            try {
                e.client.shutdown();
            } catch (Exception ignore) {
            }
        }
        entries.clear();
    }

    private static final class Entry {
        final LspClient client;
        volatile Instant lastUsed = Instant.now();

        Entry(LspClient client) {
            this.client = client;
        }

        void touch() {
            this.lastUsed = Instant.now();
        }
    }
}
