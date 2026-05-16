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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class LspTransportTest {

    private LspTransport transport;
    private PipedOutputStream serverWriter;

    private LspTransport createTransport(
            PipedInputStream clientInput, ByteArrayOutputStream clientOutput) {
        return new LspTransport(clientInput, clientOutput);
    }

    @AfterEach
    void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    void sendRequestWritesContentLengthFrame() throws Exception {
        PipedInputStream clientInput = new PipedInputStream();
        serverWriter = new PipedOutputStream(clientInput);
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();

        transport = createTransport(clientInput, clientOutput);

        String message = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        transport.sendRequest(1, message);

        String written = clientOutput.toString(StandardCharsets.UTF_8);
        assertThat(written).startsWith("Content-Length: ");
        assertThat(written).contains("\r\n\r\n");
        assertThat(written).contains(message);

        int expectedLength = message.getBytes(StandardCharsets.UTF_8).length;
        assertThat(written).contains("Content-Length: " + expectedLength);
    }

    @Test
    void sendNotificationWritesFrame() throws Exception {
        PipedInputStream clientInput = new PipedInputStream();
        serverWriter = new PipedOutputStream(clientInput);
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();

        transport = createTransport(clientInput, clientOutput);

        String notification = "{\"jsonrpc\":\"2.0\",\"method\":\"initialized\"}";
        transport.sendNotification(notification);

        String written = clientOutput.toString(StandardCharsets.UTF_8);
        assertThat(written).contains(notification);
        assertThat(written).contains("Content-Length:");
    }

    @Test
    void sendAndAwaitResponse() throws Exception {
        PipedInputStream clientInput = new PipedInputStream();
        serverWriter = new PipedOutputStream(clientInput);
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();

        transport = createTransport(clientInput, clientOutput);

        transport.sendRequest(1, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"shutdown\"}");

        String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}";
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + responseBytes.length + "\r\n\r\n";
        serverWriter.write(header.getBytes(StandardCharsets.UTF_8));
        serverWriter.write(responseBytes);
        serverWriter.flush();

        String result = transport.awaitResponse(1, 5000);
        assertThat(result).contains("\"id\":1");
        assertThat(result).contains("\"result\":null");
    }

    @Test
    void awaitResponseTimesOut() throws Exception {
        PipedInputStream clientInput = new PipedInputStream();
        serverWriter = new PipedOutputStream(clientInput);
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();

        transport = createTransport(clientInput, clientOutput);

        transport.sendRequest(99, "{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"test\"}");

        assertThatThrownBy(() -> transport.awaitResponse(99, 200))
                .isInstanceOf(LspException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void awaitResponseForUnknownIdThrows() throws Exception {
        PipedInputStream clientInput = new PipedInputStream();
        serverWriter = new PipedOutputStream(clientInput);
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();

        transport = createTransport(clientInput, clientOutput);

        assertThatThrownBy(() -> transport.awaitResponse(999))
                .isInstanceOf(LspException.class)
                .hasMessageContaining("No pending request");
    }

    @Test
    void closeCompletesExceptionallyOnPendingRequests() throws Exception {
        PipedInputStream clientInput = new PipedInputStream();
        serverWriter = new PipedOutputStream(clientInput);
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();

        transport = createTransport(clientInput, clientOutput);

        transport.sendRequest(5, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"test\"}");
        transport.close();

        assertThatThrownBy(() -> transport.awaitResponse(5, 200)).isInstanceOf(LspException.class);
    }

    @Test
    void sendAfterCloseThrows() throws Exception {
        PipedInputStream clientInput = new PipedInputStream();
        serverWriter = new PipedOutputStream(clientInput);
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();

        transport = createTransport(clientInput, clientOutput);
        transport.close();

        assertThatThrownBy(
                        () ->
                                transport.sendRequest(
                                        1, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\"}"))
                .isInstanceOf(LspException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void dispatchesOnlyResponsesNotRequests() throws Exception {
        PipedInputStream clientInput = new PipedInputStream();
        serverWriter = new PipedOutputStream(clientInput);
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();

        transport = createTransport(clientInput, clientOutput);

        transport.sendRequest(1, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\"}");

        // Server sends a request (has both id and method) — should NOT dispatch to pending
        String serverRequest =
                "{\"jsonrpc\":\"2.0\",\"id\":100,\"method\":\"window/logMessage\",\"params\":{}}";
        byte[] bytes = serverRequest.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + bytes.length + "\r\n\r\n";
        serverWriter.write(header.getBytes(StandardCharsets.UTF_8));
        serverWriter.write(bytes);
        serverWriter.flush();

        // Now send the actual response
        String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}";
        byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
        String respHeader = "Content-Length: " + respBytes.length + "\r\n\r\n";
        serverWriter.write(respHeader.getBytes(StandardCharsets.UTF_8));
        serverWriter.write(respBytes);
        serverWriter.flush();

        String result = transport.awaitResponse(1, 5000);
        assertThat(result).contains("\"ok\":true");
    }
}
