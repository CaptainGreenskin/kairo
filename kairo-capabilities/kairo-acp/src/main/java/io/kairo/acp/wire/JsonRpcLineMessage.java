/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.wire;

import com.fasterxml.jackson.databind.JsonNode;

/** Parsed JSON-RPC 2.0 frame — sealed family discriminated by which fields are present. */
public sealed interface JsonRpcLineMessage {

    record Request(Object id, String method, JsonNode params) implements JsonRpcLineMessage {}

    record Response(Object id, JsonNode result, JsonRpcError error) implements JsonRpcLineMessage {
        public boolean isSuccess() {
            return error == null;
        }
    }

    record Notification(String method, JsonNode params) implements JsonRpcLineMessage {}

    record JsonRpcError(int code, String message, JsonNode data) {}

    /** Standard JSON-RPC 2.0 error codes used by ACP. */
    final class Errors {
        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;

        private Errors() {}
    }

    static JsonRpcLineMessage classify(JsonNode root) {
        boolean hasMethod = root.has("method") && !root.get("method").isNull();
        boolean hasId = root.has("id") && !root.get("id").isNull();
        boolean hasResultOrError =
                (root.has("result") && !root.get("result").isNull())
                        || (root.has("error") && !root.get("error").isNull());

        if (hasMethod && hasId) {
            return new Request(
                    idValue(root.get("id")), root.get("method").asText(), root.get("params"));
        }
        if (hasMethod) {
            return new Notification(root.get("method").asText(), root.get("params"));
        }
        if (hasResultOrError) {
            JsonRpcError err = null;
            if (root.has("error") && !root.get("error").isNull()) {
                JsonNode en = root.get("error");
                err =
                        new JsonRpcError(
                                en.path("code").asInt(),
                                en.path("message").asText(),
                                en.get("data"));
            }
            Object id = hasId ? idValue(root.get("id")) : null;
            return new Response(id, root.get("result"), err);
        }
        throw new IllegalArgumentException("Unclassifiable JSON-RPC frame: " + root);
    }

    private static Object idValue(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isIntegralNumber()) return n.longValue();
        return n.asText();
    }
}
