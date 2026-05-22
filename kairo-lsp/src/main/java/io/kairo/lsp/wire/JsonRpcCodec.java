/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.lsp.LspException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Content-Length framed JSON-RPC 2.0 codec, the {@code base protocol} every LSP server speaks over
 * stdio. Read/write are stateless; an instance just holds an ObjectMapper so callers can share
 * encoders.
 *
 * <p>Read loop expects a sequence of headers terminated by a CRLF CRLF, with at minimum a {@code
 * Content-Length: N} header, followed by exactly N bytes of UTF-8 JSON. Other headers
 * (Content-Type, …) are tolerated and skipped.
 */
public final class JsonRpcCodec {

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] CRLFCRLF = {'\r', '\n', '\r', '\n'};

    private final ObjectMapper mapper;

    public JsonRpcCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonRpcCodec() {
        this(new ObjectMapper());
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    // ---- encode --------------------------------------------------------------------------------

    /** Build a Request envelope. {@code params} may be null. */
    public ObjectNode request(Object id, String method, JsonNode params) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        if (id instanceof Number num) n.put("id", num.longValue());
        else n.put("id", id == null ? null : id.toString());
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
        if (id instanceof Number num) n.put("id", num.longValue());
        else if (id != null) n.put("id", id.toString());
        n.set("result", result == null ? mapper.nullNode() : result);
        return n;
    }

    /** Write {@code message} (any JsonNode) to {@code out} with Content-Length framing + flush. */
    public synchronized void writeMessage(OutputStream out, JsonNode message) throws IOException {
        byte[] payload = mapper.writeValueAsBytes(message);
        String header = "Content-Length: " + payload.length + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        out.flush();
    }

    // ---- decode --------------------------------------------------------------------------------

    /**
     * Read one framed JSON-RPC message from {@code in}. Returns null on clean EOF. Throws {@link
     * LspException} for malformed framing / unparseable JSON.
     */
    public JsonRpcMessage readMessage(InputStream in) throws IOException {
        Integer contentLength = readHeaders(in);
        if (contentLength == null) return null; // clean EOF
        byte[] payload = readExact(in, contentLength);
        return parse(payload);
    }

    private Integer readHeaders(InputStream in) throws IOException {
        int length = -1;
        StringBuilder line = new StringBuilder();
        int b;
        boolean seenAnyByte = false;
        while ((b = in.read()) != -1) {
            seenAnyByte = true;
            if (b == '\r') {
                int next = in.read();
                if (next == '\n') {
                    if (line.length() == 0) {
                        if (length < 0) {
                            throw new LspException("JSON-RPC frame missing Content-Length header");
                        }
                        return length;
                    }
                    String header = line.toString();
                    int colon = header.indexOf(':');
                    if (colon > 0) {
                        String name = header.substring(0, colon).trim();
                        String value = header.substring(colon + 1).trim();
                        if (name.equalsIgnoreCase("Content-Length")) {
                            try {
                                length = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                throw new LspException("Invalid Content-Length: " + value, e);
                            }
                        }
                    }
                    line.setLength(0);
                } else if (next == -1) {
                    throw new EOFException("EOF after CR in JSON-RPC header");
                } else {
                    line.append((char) b);
                    line.append((char) next);
                }
            } else {
                line.append((char) b);
            }
        }
        if (!seenAnyByte) return null;
        throw new EOFException("EOF inside JSON-RPC headers");
    }

    private byte[] readExact(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) throw new EOFException("EOF inside JSON-RPC body, " + off + "/" + n);
            off += r;
        }
        return buf;
    }

    /** Parse a payload (no framing) into a {@link JsonRpcMessage}. */
    public JsonRpcMessage parse(byte[] payload) throws IOException {
        JsonNode root = mapper.readTree(payload);
        return classify(root);
    }

    public JsonRpcMessage classify(JsonNode root) {
        boolean hasId = root.has("id") && !root.get("id").isNull();
        boolean hasMethod = root.has("method");
        boolean hasResultOrError = root.has("result") || root.has("error");

        if (hasMethod && hasId) {
            Object id = idValue(root.get("id"));
            return new JsonRpcMessage.Request(id, root.get("method").asText(), root.get("params"));
        }
        if (hasMethod) {
            return new JsonRpcMessage.Notification(root.get("method").asText(), root.get("params"));
        }
        if (hasResultOrError) {
            Object id = root.has("id") ? idValue(root.get("id")) : null;
            JsonRpcMessage.JsonRpcError err = null;
            if (root.has("error") && !root.get("error").isNull()) {
                JsonNode en = root.get("error");
                err =
                        new JsonRpcMessage.JsonRpcError(
                                en.path("code").asInt(),
                                en.path("message").asText(),
                                en.get("data"));
            }
            return new JsonRpcMessage.Response(id, root.get("result"), err);
        }
        throw new LspException("Unclassifiable JSON-RPC frame: " + root);
    }

    private static Object idValue(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isIntegralNumber()) return n.longValue();
        return n.asText();
    }

    /** Convenience for tests / direct readers. */
    public static byte[] frame(byte[] payload) {
        String header = "Content-Length: " + payload.length + "\r\n\r\n";
        byte[] h = header.getBytes(StandardCharsets.US_ASCII);
        byte[] out = Arrays.copyOf(h, h.length + payload.length);
        System.arraycopy(payload, 0, out, h.length, payload.length);
        return out;
    }

    /** Visible for tests. */
    static byte[] crlf() {
        return CRLF.clone();
    }

    static byte[] crlfCrlf() {
        return CRLFCRLF.clone();
    }
}
