/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JsonRpcLineCodecTest {

    private final JsonRpcLineCodec codec = new JsonRpcLineCodec();

    @Test
    void writeProducesSingleLineJsonTerminatedByNewline() throws Exception {
        ObjectNode req = codec.request(7, "initialize", null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeMessage(out, req);
        String wire = out.toString(StandardCharsets.UTF_8);
        assertThat(wire).endsWith("\n");
        assertThat(wire.split("\n")).hasSize(1);
        assertThat(wire).contains("\"method\":\"initialize\"");
        assertThat(wire).contains("\"id\":7");
    }

    @Test
    void readClassifiesRequest() throws Exception {
        BufferedReader r = readerOf("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}\n");
        JsonRpcLineMessage msg = codec.readMessage(r);
        assertThat(msg).isInstanceOf(JsonRpcLineMessage.Request.class);
        var req = (JsonRpcLineMessage.Request) msg;
        assertThat(req.method()).isEqualTo("ping");
        assertThat(((Number) req.id()).longValue()).isEqualTo(1L);
    }

    @Test
    void readClassifiesNotification() throws Exception {
        BufferedReader r =
                readerOf("{\"jsonrpc\":\"2.0\",\"method\":\"session/update\",\"params\":{}}\n");
        JsonRpcLineMessage msg = codec.readMessage(r);
        assertThat(msg).isInstanceOf(JsonRpcLineMessage.Notification.class);
    }

    @Test
    void readClassifiesResponse() throws Exception {
        BufferedReader r =
                readerOf("{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"sessionId\":\"abc\"}}\n");
        JsonRpcLineMessage msg = codec.readMessage(r);
        assertThat(msg).isInstanceOf(JsonRpcLineMessage.Response.class);
        assertThat(((JsonRpcLineMessage.Response) msg).isSuccess()).isTrue();
    }

    @Test
    void readReturnsNullOnEof() throws Exception {
        BufferedReader r = readerOf("");
        assertThat(codec.readMessage(r)).isNull();
    }

    @Test
    void readSkipsBlankLines() throws Exception {
        BufferedReader r = readerOf("\n\n{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}\n");
        JsonRpcLineMessage msg = codec.readMessage(r);
        assertThat(msg).isInstanceOf(JsonRpcLineMessage.Request.class);
    }

    @Test
    void readsTwoConsecutiveFrames() throws Exception {
        BufferedReader r =
                readerOf(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"a\"}\n"
                                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"b\"}\n");
        var first = codec.readMessage(r);
        var second = codec.readMessage(r);
        assertThat(((JsonRpcLineMessage.Request) first).method()).isEqualTo("a");
        assertThat(((JsonRpcLineMessage.Request) second).method()).isEqualTo("b");
    }

    @Test
    void errorResponseShape() throws Exception {
        ObjectNode err = codec.errorResponse(3, -32601, "Method not found");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.writeMessage(out, err);
        var msg = codec.parse(out.toString(StandardCharsets.UTF_8).trim());
        var resp = (JsonRpcLineMessage.Response) msg;
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().code()).isEqualTo(-32601);
        assertThat(resp.error().message()).isEqualTo("Method not found");
    }

    private static BufferedReader readerOf(String s) {
        return new BufferedReader(
                new InputStreamReader(
                        new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8));
    }
}
