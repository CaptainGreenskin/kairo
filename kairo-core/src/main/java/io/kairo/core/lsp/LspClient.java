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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level LSP client wrapping JSON-RPC transport.
 *
 * <p>Provides typed methods for common LSP operations: goto definition, find references, hover,
 * completions. Handles the initialize/initialized handshake and shutdown sequence.
 */
public final class LspClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LspClient.class);

    private final LspTransport transport;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private volatile boolean initialized = false;

    LspClient(LspTransport transport) {
        this.transport = transport;
    }

    public void initialize(String workspaceUri) {
        int id = nextId.getAndIncrement();
        ObjectNode params = JsonRpcMessage.MAPPER.createObjectNode();
        params.put("processId", ProcessHandle.current().pid());

        ObjectNode capabilities = params.putObject("capabilities");
        ObjectNode textDoc = capabilities.putObject("textDocument");
        textDoc.putObject("definition");
        textDoc.putObject("references");
        textDoc.putObject("hover");
        textDoc.putObject("completion");

        params.put("rootUri", workspaceUri);

        String req = JsonRpcMessage.request(id, "initialize", params);
        transport.sendRequest(id, req);
        String response = transport.awaitResponse(id);
        JsonRpcMessage.parseResult(response);

        transport.sendNotification(JsonRpcMessage.notification("initialized", null));
        initialized = true;
        log.debug("LSP initialized for workspace: {}", workspaceUri);
    }

    public List<LspLocation> gotoDefinition(String fileUri, int line, int character) {
        ensureInitialized();
        int id = nextId.getAndIncrement();

        ObjectNode params = textDocPositionParams(fileUri, line, character);
        transport.sendRequest(id, JsonRpcMessage.request(id, "textDocument/definition", params));
        String response = transport.awaitResponse(id);
        JsonNode result = JsonRpcMessage.parseResult(response);

        return parseLocations(result);
    }

    public List<LspLocation> findReferences(String fileUri, int line, int character) {
        ensureInitialized();
        int id = nextId.getAndIncrement();

        ObjectNode params = textDocPositionParams(fileUri, line, character);
        ObjectNode context = params.putObject("context");
        context.put("includeDeclaration", true);

        transport.sendRequest(id, JsonRpcMessage.request(id, "textDocument/references", params));
        String response = transport.awaitResponse(id);
        JsonNode result = JsonRpcMessage.parseResult(response);

        return parseLocations(result);
    }

    public String hover(String fileUri, int line, int character) {
        ensureInitialized();
        int id = nextId.getAndIncrement();

        ObjectNode params = textDocPositionParams(fileUri, line, character);
        transport.sendRequest(id, JsonRpcMessage.request(id, "textDocument/hover", params));
        String response = transport.awaitResponse(id);
        JsonNode result = JsonRpcMessage.parseResult(response);

        if (result.isMissingNode() || result.isNull()) {
            return "";
        }
        JsonNode contents = result.path("contents");
        if (contents.isTextual()) {
            return contents.asText();
        }
        if (contents.isObject()) {
            return contents.path("value").asText("");
        }
        if (contents.isArray() && !contents.isEmpty()) {
            JsonNode first = contents.get(0);
            if (first.isTextual()) return first.asText();
            return first.path("value").asText("");
        }
        return result.toString();
    }

    public void didOpen(String fileUri, String languageId, String content) {
        ensureInitialized();
        ObjectNode params = JsonRpcMessage.MAPPER.createObjectNode();
        ObjectNode textDoc = params.putObject("textDocument");
        textDoc.put("uri", fileUri);
        textDoc.put("languageId", languageId);
        textDoc.put("version", 1);
        textDoc.put("text", content);

        transport.sendNotification(JsonRpcMessage.notification("textDocument/didOpen", params));
    }

    public void shutdown() {
        if (!initialized) return;
        int id = nextId.getAndIncrement();
        transport.sendRequest(id, JsonRpcMessage.request(id, "shutdown", null));
        try {
            transport.awaitResponse(id, 5000);
        } catch (LspException e) {
            log.debug("Shutdown response timeout (non-fatal): {}", e.getMessage());
        }
        transport.sendNotification(JsonRpcMessage.notification("exit", null));
        initialized = false;
    }

    @Override
    public void close() {
        try {
            shutdown();
        } catch (Exception e) {
            log.debug("Error during LSP shutdown: {}", e.getMessage());
        }
        transport.close();
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new LspException("LSP client not initialized. Call initialize() first.");
        }
    }

    private static ObjectNode textDocPositionParams(String fileUri, int line, int character) {
        ObjectNode params = JsonRpcMessage.MAPPER.createObjectNode();
        ObjectNode textDoc = params.putObject("textDocument");
        textDoc.put("uri", fileUri);
        ObjectNode position = params.putObject("position");
        position.put("line", line);
        position.put("character", character);
        return params;
    }

    private static List<LspLocation> parseLocations(JsonNode result) {
        List<LspLocation> locations = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode item : result) {
                locations.add(parseLocation(item));
            }
        } else if (result.isObject() && result.has("uri")) {
            locations.add(parseLocation(result));
        }
        return locations;
    }

    private static LspLocation parseLocation(JsonNode node) {
        String uri = node.path("uri").asText("");
        JsonNode range = node.path("range");
        JsonNode start = range.path("start");
        JsonNode end = range.path("end");
        return new LspLocation(
                uri,
                start.path("line").asInt(0),
                start.path("character").asInt(0),
                end.path("line").asInt(0),
                end.path("character").asInt(0));
    }
}
