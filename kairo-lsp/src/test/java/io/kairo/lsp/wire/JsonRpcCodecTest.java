/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.wire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.lsp.LspException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JsonRpcCodecTest {

    private final JsonRpcCodec codec = new JsonRpcCodec();

    @Test
    void writesContentLengthFraming() throws Exception {
        ObjectNode req = codec.request(1, "initialize", null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeMessage(out, req);

        String wire = out.toString(StandardCharsets.UTF_8);
        assertThat(wire).startsWith("Content-Length: ");
        assertThat(wire).contains("\r\n\r\n");
        assertThat(wire).contains("\"method\":\"initialize\"");
        assertThat(wire).contains("\"id\":1");
    }

    @Test
    void roundTripsRequest() throws Exception {
        ObjectNode req =
                codec.request(7, "textDocument/didOpen", codec.mapper().createObjectNode());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeMessage(out, req);

        var msg = codec.readMessage(new ByteArrayInputStream(out.toByteArray()));
        assertThat(msg).isInstanceOf(JsonRpcMessage.Request.class);
        var parsed = (JsonRpcMessage.Request) msg;
        assertThat(parsed.method()).isEqualTo("textDocument/didOpen");
        assertThat(((Number) parsed.id()).longValue()).isEqualTo(7L);
    }

    @Test
    void classifiesNotification() throws Exception {
        ObjectNode n = codec.notification("textDocument/publishDiagnostics", null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeMessage(out, n);
        var msg = codec.readMessage(new ByteArrayInputStream(out.toByteArray()));
        assertThat(msg).isInstanceOf(JsonRpcMessage.Notification.class);
    }

    @Test
    void classifiesSuccessResponse() throws Exception {
        ObjectNode r = codec.response(1, codec.mapper().createObjectNode());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeMessage(out, r);
        var msg = codec.readMessage(new ByteArrayInputStream(out.toByteArray()));
        assertThat(msg).isInstanceOf(JsonRpcMessage.Response.class);
        assertThat(((JsonRpcMessage.Response) msg).isSuccess()).isTrue();
    }

    @Test
    void classifiesErrorResponse() throws Exception {
        ObjectNode m = codec.mapper().createObjectNode();
        m.put("jsonrpc", "2.0");
        m.put("id", 5);
        ObjectNode err = m.putObject("error");
        err.put("code", -32601);
        err.put("message", "Method not found");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeMessage(out, m);
        var msg = codec.readMessage(new ByteArrayInputStream(out.toByteArray()));
        assertThat(msg).isInstanceOf(JsonRpcMessage.Response.class);
        var r = (JsonRpcMessage.Response) msg;
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.error().code()).isEqualTo(-32601);
        assertThat(r.error().message()).isEqualTo("Method not found");
    }

    @Test
    void readsCleanEofAsNull() throws Exception {
        var msg = codec.readMessage(new ByteArrayInputStream(new byte[0]));
        assertThat(msg).isNull();
    }

    @Test
    void rejectsFrameMissingContentLength() {
        String bad = "Content-Type: application/json\r\n\r\n{}";
        assertThatThrownBy(
                        () ->
                                codec.readMessage(
                                        new ByteArrayInputStream(
                                                bad.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(LspException.class)
                .hasMessageContaining("Content-Length");
    }

    @Test
    void readsTwoConsecutiveFrames() throws Exception {
        ObjectNode a = codec.request(1, "a", null);
        ObjectNode b = codec.request(2, "b", null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeMessage(out, a);
        codec.writeMessage(out, b);
        var in = new ByteArrayInputStream(out.toByteArray());
        var first = codec.readMessage(in);
        var second = codec.readMessage(in);
        assertThat(first).isInstanceOf(JsonRpcMessage.Request.class);
        assertThat(second).isInstanceOf(JsonRpcMessage.Request.class);
        assertThat(((JsonRpcMessage.Request) second).method()).isEqualTo("b");
    }

    @Test
    void framingHelperMatchesWriter() throws Exception {
        ObjectNode req = codec.request(99, "ping", null);
        byte[] payload = codec.mapper().writeValueAsBytes(req);
        byte[] framed = JsonRpcCodec.frame(payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeMessage(out, req);
        assertThat(framed).isEqualTo(out.toByteArray());
    }
}
