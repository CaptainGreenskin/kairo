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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.annotation.Nullable;

/**
 * JSON-RPC 2.0 message for LSP communication.
 *
 * <p>Handles both request and response envelopes per the LSP specification.
 */
final class JsonRpcMessage {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonRpcMessage() {}

    static String request(int id, String method, @Nullable JsonNode params) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        node.put("method", method);
        if (params != null) {
            node.set("params", params);
        }
        return node.toString();
    }

    static String notification(String method, @Nullable JsonNode params) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("method", method);
        if (params != null) {
            node.set("params", params);
        }
        return node.toString();
    }

    static JsonNode parseResult(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.has("error")) {
                JsonNode error = node.get("error");
                throw new LspException(
                        "LSP error "
                                + error.path("code").asInt()
                                + ": "
                                + error.path("message").asText());
            }
            return node.path("result");
        } catch (LspException e) {
            throw e;
        } catch (Exception e) {
            throw new LspException("Failed to parse JSON-RPC response: " + e.getMessage(), e);
        }
    }
}
