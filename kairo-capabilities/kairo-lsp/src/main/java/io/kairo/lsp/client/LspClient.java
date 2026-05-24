/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.DiagnosticSeverity;
import io.kairo.api.lsp.LspException;
import io.kairo.api.lsp.Range;
import io.kairo.api.lsp.ServerDef;
import io.kairo.lsp.wire.JsonRpcCodec;
import io.kairo.lsp.wire.JsonRpcMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One LSP subprocess bound to one workspace root. Owns the {@link Process}, the reader thread, the
 * in-flight request map, and the per-document diagnostics store fed by {@code
 * textDocument/publishDiagnostics} push notifications.
 *
 * <p>Lifecycle: {@link #start()} → arbitrarily many {@link #openOrUpdate(Path, String)} + {@link
 * #waitForDiagnostics(Path, Duration)} → {@link #shutdown()}.
 *
 * <p>Single instance is NOT thread-safe for concurrent open/update on the same path (LSP version
 * counters need monotonic ordering); the higher-level {@code LspClientPool} serializes per-path
 * access. Cross-path operations are fine in parallel.
 */
public class LspClient {

    private static final Logger log = LoggerFactory.getLogger(LspClient.class);

    private final ServerDef def;
    private final Path workspaceRoot;
    private final ProcessSpawner spawner;
    private final JsonRpcCodec codec;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Map<String, DocumentState> openDocs = new ConcurrentHashMap<>();
    private final Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>();
    private final Map<String, List<DiagnosticsWaiter>> waiters = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean broken = new AtomicBoolean(false);
    private final Object writeLock = new Object();

    private Process process;
    private OutputStream stdin;
    private Thread reader;
    private Thread stderrPump;
    private ScheduledExecutorService scheduler;

    public LspClient(ServerDef def, Path workspaceRoot) {
        this(def, workspaceRoot, ProcessSpawner.DEFAULT, new JsonRpcCodec());
    }

    public LspClient(
            ServerDef def, Path workspaceRoot, ProcessSpawner spawner, JsonRpcCodec codec) {
        this.def = def;
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.spawner = spawner;
        this.codec = codec;
    }

    public ServerDef def() {
        return def;
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isBroken() {
        return broken.get();
    }

    /**
     * Spawn the subprocess and complete the LSP initialize handshake. Throws {@link LspException}
     * on spawn failure; subsequent calls observe {@link #isBroken()}.
     */
    public synchronized void start() {
        if (running.get()) return;
        try {
            this.process = spawner.spawn(def.command(), workspaceRoot);
            this.stdin = process.getOutputStream();
            InputStream stdout = process.getInputStream();
            InputStream stderr = process.getErrorStream();

            this.scheduler =
                    Executors.newSingleThreadScheduledExecutor(
                            daemonFactory("lsp-" + def.serverId() + "-timeout"));

            // Flip the running flag BEFORE starting the reader so it does not exit on its first
            // loop check before initialize() has had a chance to write the first request.
            running.set(true);

            this.reader = new Thread(() -> runReader(stdout), "lsp-" + def.serverId() + "-reader");
            this.reader.setDaemon(true);
            this.reader.start();

            this.stderrPump =
                    new Thread(() -> drainStderr(stderr), "lsp-" + def.serverId() + "-stderr");
            this.stderrPump.setDaemon(true);
            this.stderrPump.start();

            initialize();
        } catch (Exception e) {
            broken.set(true);
            running.set(false);
            try {
                if (process != null) process.destroyForcibly();
            } catch (Exception ignore) {
            }
            throw new LspException("Failed to start LSP server " + def.serverId(), e);
        }
    }

    /** Send {@code initialize} + {@code initialized} per the LSP spec. */
    private void initialize() throws Exception {
        ObjectMapper m = codec.mapper();
        ObjectNode params = m.createObjectNode();
        params.put("processId", ProcessHandle.current().pid());
        params.put("rootUri", workspaceRoot.toUri().toString());
        params.set("capabilities", clientCapabilities(m));
        ObjectNode workspaceFolder = m.createObjectNode();
        workspaceFolder.put("uri", workspaceRoot.toUri().toString());
        workspaceFolder.put(
                "name",
                workspaceRoot.getFileName() == null
                        ? "root"
                        : workspaceRoot.getFileName().toString());
        ArrayNode wf = m.createArrayNode().add(workspaceFolder);
        params.set("workspaceFolders", wf);

        try {
            requestSync("initialize", params, Duration.ofSeconds(30));
        } catch (TimeoutException te) {
            throw new LspException("LSP initialize timed out for " + def.serverId(), te);
        }
        notify("initialized", m.createObjectNode());
    }

    private static ObjectNode clientCapabilities(ObjectMapper m) {
        ObjectNode caps = m.createObjectNode();
        ObjectNode textDocument = m.createObjectNode();
        ObjectNode publishDiagnostics = m.createObjectNode();
        publishDiagnostics.put("relatedInformation", false);
        publishDiagnostics.put("versionSupport", true);
        textDocument.set("publishDiagnostics", publishDiagnostics);
        ObjectNode synchronization = m.createObjectNode();
        synchronization.put("dynamicRegistration", false);
        synchronization.put("didSave", true);
        textDocument.set("synchronization", synchronization);
        caps.set("textDocument", textDocument);
        ObjectNode workspace = m.createObjectNode();
        workspace.put("workspaceFolders", true);
        workspace.put("configuration", true);
        caps.set("workspace", workspace);
        return caps;
    }

    /** Open the document or send a textDocument/didChange with the new full content. */
    public void openOrUpdate(Path file, String content) {
        ensureRunning();
        String uri = file.toAbsolutePath().normalize().toUri().toString();
        DocumentState state = openDocs.computeIfAbsent(uri, k -> new DocumentState());
        ObjectMapper m = codec.mapper();
        synchronized (state) {
            if (state.opened) {
                state.version++;
                ObjectNode params = m.createObjectNode();
                ObjectNode td = m.createObjectNode();
                td.put("uri", uri);
                td.put("version", state.version);
                params.set("textDocument", td);
                ArrayNode changes = m.createArrayNode();
                ObjectNode change = m.createObjectNode();
                change.put("text", content);
                changes.add(change);
                params.set("contentChanges", changes);
                notify("textDocument/didChange", params);
            } else {
                state.opened = true;
                state.version = 1;
                ObjectNode params = m.createObjectNode();
                ObjectNode td = m.createObjectNode();
                td.put("uri", uri);
                td.put("languageId", def.languageId());
                td.put("version", state.version);
                td.put("text", content);
                params.set("textDocument", td);
                notify("textDocument/didOpen", params);
            }
        }
    }

    /**
     * Wait until publishDiagnostics has been received for {@code file} OR until {@code timeout}
     * expires, then return the latest snapshot of diagnostics for that uri (empty list if none).
     */
    public List<Diagnostic> waitForDiagnostics(Path file, Duration timeout) {
        ensureRunning();
        String uri = file.toAbsolutePath().normalize().toUri().toString();
        DiagnosticsWaiter waiter = new DiagnosticsWaiter();
        waiters.computeIfAbsent(uri, k -> new ArrayList<>()).add(waiter);
        try {
            // If diagnostics for this uri were ALREADY pushed since the last waiter we still need
            // to wait for the next push (the new edit's diagnostics). The current pattern: caller
            // openOrUpdate first, then waitForDiagnostics. The next push is "ours".
            waiter.latch.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.debug("LSP {} timed out waiting for diagnostics on {}", def.serverId(), uri);
        } catch (Exception e) {
            log.debug("LSP {} interrupted waiting for diagnostics", def.serverId(), e);
            Thread.currentThread().interrupt();
        } finally {
            List<DiagnosticsWaiter> bucket = waiters.get(uri);
            if (bucket != null) bucket.remove(waiter);
        }
        return currentDiagnostics(file);
    }

    public List<Diagnostic> currentDiagnostics(Path file) {
        String uri = file.toAbsolutePath().normalize().toUri().toString();
        List<Diagnostic> snap = diagnostics.get(uri);
        return snap == null ? List.of() : List.copyOf(snap);
    }

    public synchronized void shutdown() {
        if (!running.get()) return;
        running.set(false);
        try {
            requestSync("shutdown", codec.mapper().nullNode(), Duration.ofSeconds(2));
        } catch (Exception ignore) {
        }
        try {
            notify("exit", null);
        } catch (Exception ignore) {
        }
        try {
            if (process != null && !process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
        }
        for (var f : pending.values()) f.completeExceptionally(new LspException("LSP shutdown"));
        pending.clear();
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ---- internals -----------------------------------------------------------------------------

    private void runReader(InputStream in) {
        try {
            while (running.get()) {
                JsonRpcMessage msg;
                try {
                    msg = codec.readMessage(in);
                } catch (IOException e) {
                    if (running.get()) {
                        log.debug("LSP {} reader IO error", def.serverId(), e);
                        broken.set(true);
                    }
                    return;
                }
                if (msg == null) {
                    log.debug("LSP {} reader saw clean EOF", def.serverId());
                    return;
                }
                dispatch(msg);
            }
        } finally {
            running.set(false);
            for (var f : pending.values())
                f.completeExceptionally(new LspException("LSP reader exited"));
            pending.clear();
            // unblock anything waiting for diagnostics that will never come
            for (var bucket : waiters.values()) for (var w : bucket) w.latch.complete(null);
        }
    }

    private void drainStderr(InputStream err) {
        try {
            byte[] buf = new byte[2048];
            int n;
            StringBuilder line = new StringBuilder();
            while ((n = err.read(buf)) > 0) {
                for (int i = 0; i < n; i++) {
                    char c = (char) (buf[i] & 0xff);
                    if (c == '\n') {
                        if (!line.isEmpty()) log.debug("LSP {} stderr: {}", def.serverId(), line);
                        line.setLength(0);
                    } else if (c != '\r') {
                        line.append(c);
                        if (line.length() > 4096) {
                            log.debug("LSP {} stderr: {}", def.serverId(), line);
                            line.setLength(0);
                        }
                    }
                }
            }
        } catch (IOException ignore) {
        }
    }

    private void dispatch(JsonRpcMessage msg) {
        if (msg instanceof JsonRpcMessage.Response r) {
            if (r.id() instanceof Number num) {
                CompletableFuture<JsonNode> f = pending.remove(num.longValue());
                if (f != null) {
                    if (r.isSuccess()) f.complete(r.result());
                    else
                        f.completeExceptionally(
                                new LspException("LSP request failed: " + r.error().message()));
                }
            }
        } else if (msg instanceof JsonRpcMessage.Notification n) {
            handleNotification(n);
        } else if (msg instanceof JsonRpcMessage.Request req) {
            // server→client request; we acknowledge the few we need.
            handleServerRequest(req);
        }
    }

    private void handleNotification(JsonRpcMessage.Notification n) {
        if ("textDocument/publishDiagnostics".equals(n.method())) {
            JsonNode params = n.params();
            if (params == null || !params.has("uri")) return;
            String uri = params.get("uri").asText();
            List<Diagnostic> parsed = parseDiagnostics(params);
            diagnostics.put(uri, parsed);
            List<DiagnosticsWaiter> bucket = waiters.get(uri);
            if (bucket != null) {
                for (DiagnosticsWaiter w : List.copyOf(bucket)) {
                    w.latch.complete(null);
                }
            }
        }
    }

    private void handleServerRequest(JsonRpcMessage.Request req) {
        ObjectMapper m = codec.mapper();
        try {
            // Empty result is the safe default for the few requests servers actually fire at us.
            ObjectNode resp = codec.response(req.id(), m.createArrayNode());
            writeRaw(resp);
        } catch (Exception e) {
            log.debug("Failed to respond to server request {}", req.method(), e);
        }
    }

    private List<Diagnostic> parseDiagnostics(JsonNode params) {
        String uri = params.get("uri").asText();
        JsonNode arr = params.get("diagnostics");
        if (arr == null || !arr.isArray()) return List.of();
        List<Diagnostic> out = new ArrayList<>(arr.size());
        for (JsonNode d : arr) {
            JsonNode r = d.get("range");
            if (r == null) continue;
            JsonNode start = r.get("start");
            JsonNode end = r.get("end");
            if (start == null || end == null) continue;
            Range range;
            try {
                range =
                        new Range(
                                start.path("line").asInt(),
                                start.path("character").asInt(),
                                end.path("line").asInt(),
                                end.path("character").asInt());
            } catch (IllegalArgumentException e) {
                continue;
            }
            int sev = d.path("severity").asInt(2);
            String msg = d.path("message").asText("");
            String code = d.has("code") ? d.get("code").asText() : null;
            String source = d.has("source") ? d.get("source").asText() : null;
            out.add(
                    new Diagnostic(
                            uri, range, DiagnosticSeverity.fromWire(sev), msg, code, source));
        }
        return out;
    }

    // ---- write helpers -------------------------------------------------------------------------

    private void notify(String method, JsonNode params) {
        ObjectNode n = codec.notification(method, params);
        writeRaw(n);
    }

    private JsonNode requestSync(String method, JsonNode params, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        long id = nextId.getAndIncrement();
        CompletableFuture<JsonNode> f = new CompletableFuture<>();
        pending.put(id, f);
        try {
            ObjectNode req = codec.request(id, method, params);
            writeRaw(req);
            return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            pending.remove(id);
        }
    }

    private void writeRaw(ObjectNode node) {
        synchronized (writeLock) {
            try {
                codec.writeMessage(stdin, node);
            } catch (IOException e) {
                broken.set(true);
                throw new LspException("LSP write failed for " + def.serverId(), e);
            }
        }
    }

    private void ensureRunning() {
        if (broken.get()) throw new LspException("LSP server " + def.serverId() + " is broken");
        if (!running.get()) throw new LspException("LSP server " + def.serverId() + " not started");
    }

    private static java.util.concurrent.ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    private static final class DocumentState {
        boolean opened;
        long version;
    }

    private static final class DiagnosticsWaiter {
        final CompletableFuture<Void> latch = new CompletableFuture<>();
    }

    /** Visible for tests so they can stub in a Process backed by ByteArrayInputStream pipes. */
    public Set<String> openedUris() {
        return new HashSet<>(openDocs.keySet());
    }

    public Map<String, List<Diagnostic>> diagnosticsSnapshot() {
        Map<String, List<Diagnostic>> out = new HashMap<>();
        diagnostics.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return out;
    }
}
