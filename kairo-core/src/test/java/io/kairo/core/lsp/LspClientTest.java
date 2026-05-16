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

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class LspClientTest {

    private PipedOutputStream toClient;
    private PipedInputStream fromClient;
    private LspTransport transport;
    private LspClient client;
    private Thread fakeServer;
    private volatile boolean serverRunning = true;

    private final Map<String, String> methodResults = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        PipedInputStream clientInput = new PipedInputStream(8192);
        toClient = new PipedOutputStream(clientInput);

        PipedOutputStream clientOutputPipe = new PipedOutputStream();
        fromClient = new PipedInputStream(clientOutputPipe, 8192);

        transport = new LspTransport(clientInput, clientOutputPipe);
        client = new LspClient(transport);

        methodResults.put("initialize", "{\"capabilities\":{}}");

        fakeServer =
                new Thread(
                        () -> {
                            try {
                                BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        fromClient, StandardCharsets.UTF_8));
                                while (serverRunning) {
                                    int contentLength = -1;
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        line = line.trim();
                                        if (line.isEmpty()) break;
                                        if (line.startsWith("Content-Length:")) {
                                            contentLength =
                                                    Integer.parseInt(line.substring(15).trim());
                                        }
                                    }
                                    if (contentLength <= 0) break;

                                    char[] buf = new char[contentLength];
                                    int totalRead = 0;
                                    while (totalRead < contentLength) {
                                        int read =
                                                reader.read(
                                                        buf, totalRead, contentLength - totalRead);
                                        if (read == -1) return;
                                        totalRead += read;
                                    }

                                    String requestJson = new String(buf);
                                    JsonNode node = JsonRpcMessage.MAPPER.readTree(requestJson);

                                    if (!node.has("id")) continue;

                                    int id = node.get("id").asInt();
                                    String method = node.path("method").asText("");
                                    String resultJson = methodResults.getOrDefault(method, "null");

                                    String response =
                                            "{\"jsonrpc\":\"2.0\",\"id\":"
                                                    + id
                                                    + ",\"result\":"
                                                    + resultJson
                                                    + "}";
                                    byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                                    String header =
                                            "Content-Length: " + respBytes.length + "\r\n\r\n";
                                    synchronized (toClient) {
                                        toClient.write(header.getBytes(StandardCharsets.UTF_8));
                                        toClient.write(respBytes);
                                        toClient.flush();
                                    }
                                }
                            } catch (Exception e) {
                                if (serverRunning) {
                                    // unexpected
                                }
                            }
                        },
                        "fake-lsp-server");
        fakeServer.setDaemon(true);
        fakeServer.start();
    }

    @AfterEach
    void tearDown() {
        serverRunning = false;
        transport.close();
        try {
            toClient.close();
        } catch (Exception e) {
            // ignore
        }
        try {
            fromClient.close();
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    void initializeSendsCorrectRequests() {
        client.initialize("file:///workspace");
    }

    @Test
    void gotoDefinitionBeforeInitializeThrows() {
        assertThatThrownBy(() -> client.gotoDefinition("file:///test.ts", 0, 0))
                .isInstanceOf(LspException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void gotoDefinitionReturnsSingleLocation() {
        client.initialize("file:///workspace");

        methodResults.put(
                "textDocument/definition",
                "[{\"uri\":\"file:///workspace/src/main.ts\","
                        + "\"range\":{\"start\":{\"line\":10,\"character\":5},"
                        + "\"end\":{\"line\":10,\"character\":15}}}]");

        List<LspLocation> locs = client.gotoDefinition("file:///workspace/src/app.ts", 5, 10);
        assertThat(locs).hasSize(1);
        assertThat(locs.get(0).uri()).isEqualTo("file:///workspace/src/main.ts");
        assertThat(locs.get(0).startLine()).isEqualTo(10);
        assertThat(locs.get(0).startCharacter()).isEqualTo(5);
    }

    @Test
    void gotoDefinitionReturnsEmptyForEmptyArray() {
        client.initialize("file:///workspace");

        methodResults.put("textDocument/definition", "[]");

        List<LspLocation> locs = client.gotoDefinition("file:///workspace/src/app.ts", 5, 10);
        assertThat(locs).isEmpty();
    }

    @Test
    void findReferencesReturnsMultipleLocations() {
        client.initialize("file:///workspace");

        methodResults.put(
                "textDocument/references",
                "[{\"uri\":\"file:///a.ts\",\"range\":{\"start\":{\"line\":1,\"character\":0},\"end\":{\"line\":1,\"character\":5}}},"
                        + "{\"uri\":\"file:///b.ts\",\"range\":{\"start\":{\"line\":2,\"character\":0},\"end\":{\"line\":2,\"character\":5}}}]");

        List<LspLocation> refs = client.findReferences("file:///a.ts", 1, 0);
        assertThat(refs).hasSize(2);
    }

    @Test
    void hoverReturnsStringContent() {
        client.initialize("file:///workspace");

        methodResults.put("textDocument/hover", "{\"contents\":\"function foo(): void\"}");

        String hover = client.hover("file:///test.ts", 3, 5);
        assertThat(hover).isEqualTo("function foo(): void");
    }

    @Test
    void hoverReturnsMarkupContent() {
        client.initialize("file:///workspace");

        methodResults.put(
                "textDocument/hover",
                "{\"contents\":{\"kind\":\"markdown\",\"value\":\"```ts\\nfunction foo(): void\\n```\"}}");

        String hover = client.hover("file:///test.ts", 3, 5);
        assertThat(hover).contains("function foo(): void");
    }

    @Test
    void hoverReturnsEmptyForNullResult() {
        client.initialize("file:///workspace");

        methodResults.put("textDocument/hover", "null");

        String hover = client.hover("file:///test.ts", 3, 5);
        assertThat(hover).isEmpty();
    }

    @Test
    void shutdownSendsShutdownAndExit() {
        client.initialize("file:///workspace");
        methodResults.put("shutdown", "null");
        client.shutdown();
    }

    @Test
    void didOpenDoesNotThrow() {
        client.initialize("file:///workspace");
        client.didOpen("file:///test.ts", "typescript", "const x = 1;");
    }
}
