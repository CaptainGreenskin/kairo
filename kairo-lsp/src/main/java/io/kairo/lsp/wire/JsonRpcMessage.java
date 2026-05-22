/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.wire;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One JSON-RPC 2.0 frame after parsing. We keep three flavors discriminated by which fields are
 * present, matching the LSP usage:
 *
 * <ul>
 *   <li>{@code Request} — has {@code id} and {@code method}; expects a response.
 *   <li>{@code Response} — has {@code id} and either {@code result} or {@code error}.
 *   <li>{@code Notification} — has {@code method} but no {@code id}; fire-and-forget.
 * </ul>
 */
public sealed interface JsonRpcMessage {

    record Request(Object id, String method, JsonNode params) implements JsonRpcMessage {}

    record Response(Object id, JsonNode result, JsonRpcError error) implements JsonRpcMessage {
        public boolean isSuccess() {
            return error == null;
        }
    }

    record Notification(String method, JsonNode params) implements JsonRpcMessage {}

    record JsonRpcError(int code, String message, JsonNode data) {}
}
