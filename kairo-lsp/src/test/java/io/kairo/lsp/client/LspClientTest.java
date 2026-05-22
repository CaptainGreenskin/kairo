/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.lsp.Diagnostic;
import io.kairo.api.lsp.ServerDef;
import io.kairo.lsp.wire.JsonRpcCodec;
import io.kairo.lsp.wire.JsonRpcMessage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end exercise of {@link LspClient} via {@link FakeProcess} + an in-test "server thread"
 * that speaks the LSP wire protocol. No real subprocesses, no OS dependencies.
 */
class LspClientTest {

    private static final ServerDef TEST_SERVER =
            new ServerDef(
                    "fake-lsp",
                    "Fake LSP",
                    Set.of("py"),
                    Set.of(),
                    List.of("fake-lsp-bin"),
                    "python");

    @Test
    void initializeAndDiagnosticsRoundtrip(@TempDir Path tmp) throws Exception {
        FakeProcess proc = new FakeProcess();
        Path file = tmp.resolve("hello.py");
        java.nio.file.Files.writeString(file, "print('hi')\n");

        CompletableFuture<String> diagnosticUri = new CompletableFuture<>();
        Thread server = new Thread(() -> runFakeServer(proc, diagnosticUri), "fake-lsp-server");
        server.setDaemon(true);
        server.start();

        LspClient client = new LspClient(TEST_SERVER, tmp, (cmd, cwd) -> proc, new JsonRpcCodec());
        try {
            client.start();
            client.openOrUpdate(file, "print('hi')\n");

            // Wait until the fake server has actually pushed diagnostics — then ask the client.
            String uri = diagnosticUri.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(uri).startsWith("file:");

            List<Diagnostic> got = client.waitForDiagnostics(file, Duration.ofSeconds(2));
            assertThat(got).hasSize(1);
            assertThat(got.get(0).message()).isEqualTo("syntax error");
        } finally {
            client.shutdown();
            proc.destroy();
        }
    }

    /**
     * Tolerant LSP server: reads frames in a loop, responds to any "initialize" request, and pushes
     * one diagnostic the first time it sees a {@code textDocument/didOpen} notification.
     */
    private void runFakeServer(FakeProcess proc, CompletableFuture<String> diagnosticUri) {
        ObjectMapper m = new ObjectMapper();
        JsonRpcCodec codec = new JsonRpcCodec(m);
        try {
            while (true) {
                JsonRpcMessage msg = codec.readMessage(proc.clientStdinReader);
                if (msg == null) return;
                if (msg instanceof JsonRpcMessage.Request req) {
                    if ("initialize".equals(req.method())) {
                        var initResult = m.createObjectNode();
                        initResult.putObject("capabilities").put("textDocumentSync", 1);
                        codec.writeMessage(proc.serverWriter, codec.response(req.id(), initResult));
                    } else if ("shutdown".equals(req.method())) {
                        codec.writeMessage(
                                proc.serverWriter, codec.response(req.id(), m.nullNode()));
                        return;
                    } else {
                        codec.writeMessage(
                                proc.serverWriter, codec.response(req.id(), m.createObjectNode()));
                    }
                } else if (msg instanceof JsonRpcMessage.Notification n) {
                    if ("textDocument/didOpen".equals(n.method())) {
                        JsonNode params = n.params();
                        String uri = params.path("textDocument").path("uri").asText();
                        pushOneDiagnostic(codec, m, proc, uri);
                        diagnosticUri.complete(uri);
                    } else if ("exit".equals(n.method())) {
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            diagnosticUri.completeExceptionally(t);
        }
    }

    private static void pushOneDiagnostic(
            JsonRpcCodec codec, ObjectMapper m, FakeProcess proc, String uri) throws Exception {
        var publishParams = m.createObjectNode();
        publishParams.put("uri", uri);
        var diagArr = publishParams.putArray("diagnostics");
        var d = diagArr.addObject();
        var r = d.putObject("range");
        r.putObject("start").put("line", 0).put("character", 0);
        r.putObject("end").put("line", 0).put("character", 5);
        d.put("severity", 1).put("message", "syntax error");

        codec.writeMessage(
                proc.serverWriter,
                codec.notification("textDocument/publishDiagnostics", publishParams));
    }
}
