/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.core.lsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stdio-based JSON-RPC transport for LSP. Manages reading/writing Content-Length framed messages
 * over stdin/stdout of a language server process.
 */
final class LspTransport implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LspTransport.class);
    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    private final OutputStream output;
    private final BufferedReader reader;
    private final Map<Integer, CompletableFuture<String>> pendingRequests =
            new ConcurrentHashMap<>();
    private final Thread readerThread;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    LspTransport(InputStream input, OutputStream output) {
        this.output = output;
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.readerThread = new Thread(this::readLoop, "lsp-reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    void sendRequest(int id, String jsonRpcMessage) {
        pendingRequests.put(id, new CompletableFuture<>());
        sendRaw(jsonRpcMessage);
    }

    void sendNotification(String jsonRpcMessage) {
        sendRaw(jsonRpcMessage);
    }

    String awaitResponse(int id) {
        return awaitResponse(id, DEFAULT_TIMEOUT_MS);
    }

    String awaitResponse(int id, long timeoutMs) {
        CompletableFuture<String> future = pendingRequests.get(id);
        if (future == null) {
            throw new LspException("No pending request for id: " + id);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(id);
            throw new LspException("LSP request timed out after " + timeoutMs + "ms");
        } catch (Exception e) {
            pendingRequests.remove(id);
            throw new LspException("Failed to get LSP response: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            readerThread.interrupt();
            pendingRequests
                    .values()
                    .forEach(f -> f.completeExceptionally(new LspException("Transport closed")));
            pendingRequests.clear();
        }
    }

    private void sendRaw(String message) {
        if (closed.get()) {
            throw new LspException("Transport is closed");
        }
        byte[] content = message.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + content.length + "\r\n\r\n";
        try {
            synchronized (output) {
                output.write(header.getBytes(StandardCharsets.UTF_8));
                output.write(content);
                output.flush();
            }
        } catch (IOException e) {
            throw new LspException("Failed to send LSP message: " + e.getMessage(), e);
        }
    }

    private void readLoop() {
        try {
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                String message = readMessage();
                if (message == null) {
                    break;
                }
                dispatchMessage(message);
            }
        } catch (IOException e) {
            if (!closed.get()) {
                log.warn("LSP reader terminated: {}", e.getMessage());
            }
        }
    }

    private String readMessage() throws IOException {
        int contentLength = -1;
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }
            if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.substring(15).trim());
            }
        }
        if (contentLength <= 0) {
            return null;
        }
        char[] buf = new char[contentLength];
        int totalRead = 0;
        while (totalRead < contentLength) {
            int read = reader.read(buf, totalRead, contentLength - totalRead);
            if (read == -1) {
                return null;
            }
            totalRead += read;
        }
        return new String(buf);
    }

    private void dispatchMessage(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = JsonRpcMessage.MAPPER.readTree(json);
            if (node.has("id") && !node.has("method")) {
                int id = node.get("id").asInt();
                CompletableFuture<String> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(json);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to dispatch LSP message: {}", e.getMessage());
        }
    }
}
