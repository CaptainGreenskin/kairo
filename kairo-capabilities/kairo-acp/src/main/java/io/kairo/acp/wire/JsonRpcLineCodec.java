/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Line-delimited JSON-RPC 2.0 codec.
 *
 * <p>ACP frames each JSON-RPC message as ONE line of UTF-8 JSON terminated by {@code \n}. This is
 * different from the LSP base protocol used by {@code kairo-lsp} (Content-Length headers) — the
 * JSON shape is the same, only the framing differs.
 *
 * <p>The codec is intentionally tiny: read a line, parse it; serialize a node, append a newline,
 * flush. Classification (Request / Response / Notification) is delegated to {@link
 * JsonRpcLineMessage}.
 *
 * <p>Thread-safety: {@link #writeMessage} synchronizes on the output stream; readers are
 * single-threaded by convention (one reader thread per process).
 */
public final class JsonRpcLineCodec {

    private final ObjectMapper mapper;

    public JsonRpcLineCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonRpcLineCodec() {
        this(new ObjectMapper());
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    // ---- read ---------------------------------------------------------------------------------

    /** Read one framed message. Returns null on clean EOF. */
    public JsonRpcLineMessage readMessage(BufferedReader in) throws IOException {
        String line;
        // Skip blank lines tolerantly — some clients send a trailing newline pair.
        while ((line = in.readLine()) != null) {
            if (!line.isBlank()) break;
        }
        if (line == null) return null;
        JsonNode root = mapper.readTree(line);
        return JsonRpcLineMessage.classify(root);
    }

    // ---- write --------------------------------------------------------------------------------

    public synchronized void writeMessage(OutputStream out, JsonNode message) throws IOException {
        byte[] payload = mapper.writeValueAsBytes(message);
        out.write(payload);
        out.write('\n');
        out.flush();
    }

    // ---- envelope builders ---------------------------------------------------------------------

    public ObjectNode request(Object id, String method, JsonNode params) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        putId(n, id);
        n.put("method", method);
        if (params != null) n.set("params", params);
        return n;
    }

    public ObjectNode notification(String method, JsonNode params) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        n.put("method", method);
        if (params != null) n.set("params", params);
        return n;
    }

    public ObjectNode response(Object id, JsonNode result) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        putId(n, id);
        n.set("result", result == null ? mapper.nullNode() : result);
        return n;
    }

    public ObjectNode errorResponse(Object id, int code, String message) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        putId(n, id);
        ObjectNode err = n.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return n;
    }

    private static void putId(ObjectNode n, Object id) {
        if (id == null) {
            n.putNull("id");
        } else if (id instanceof Number num) {
            n.put("id", num.longValue());
        } else {
            n.put("id", id.toString());
        }
    }

    /** Visible for tests: serialize a single message to its on-wire bytes (with trailing \n). */
    public byte[] frame(JsonNode message) {
        try {
            byte[] body = mapper.writeValueAsBytes(message);
            byte[] out = new byte[body.length + 1];
            System.arraycopy(body, 0, out, 0, body.length);
            out[body.length] = '\n';
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("frame failed", e);
        }
    }

    /** Visible for tests: parse a single payload (no framing). */
    public JsonRpcLineMessage parse(String payload) throws IOException {
        return JsonRpcLineMessage.classify(mapper.readTree(payload));
    }

    /** Utility for tests building inputs from a list of objects. */
    public static String charset(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
