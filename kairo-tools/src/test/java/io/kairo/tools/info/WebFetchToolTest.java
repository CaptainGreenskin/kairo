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
package io.kairo.tools.info;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WebFetchToolTest {

    private static HttpServer server;
    private static int port;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;

        server.createContext(
                "/hello",
                exchange -> {
                    byte[] body = "Hello, World!".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.createContext(
                "/json",
                exchange -> {
                    byte[] body = "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.createContext(
                "/binary",
                exchange -> {
                    byte[] body = new byte[] {(byte) 0xFF, (byte) 0xD8};
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.createContext(
                "/notfound",
                exchange -> {
                    byte[] body = "Not Found".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(404, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.createContext(
                "/slow",
                exchange -> {
                    try {
                        Thread.sleep(5_000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    byte[] body = "late".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    /** Production tool blocks localhost — use allowLocalhost=true variant for server tests. */
    private static WebFetchTool tool() {
        return new WebFetchTool(true);
    }

    @Test
    void successfulTextFetch() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/hello"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("Hello, World!");
        assertThat(result.metadata()).containsEntry("statusCode", 200);
    }

    @Test
    void jsonContentTypeAccepted() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/json"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("\"key\"");
    }

    @Test
    void binaryContentRejected() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/binary"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("non-text content");
    }

    @Test
    void httpErrorStatusIsError() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/notfound"));
        assertThat(result.isError()).isTrue();
        assertThat(result.metadata()).containsEntry("statusCode", 404);
    }

    @Test
    void nonHttpUrlRejected() {
        ToolResult result = tool().execute(Map.of("url", "ftp://example.com/file.txt"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("http:// or https://");
    }

    @Test
    void ssrfProtectionBlocksLocalhostByDefault() {
        WebFetchTool productionTool = new WebFetchTool();
        ToolResult result =
                productionTool.execute(Map.of("url", "http://localhost:" + port + "/hello"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("SSRF protection");
    }

    @Test
    void ssrfProtectionBlocks127() {
        WebFetchTool productionTool = new WebFetchTool();
        ToolResult result =
                productionTool.execute(Map.of("url", "http://127.0.0.1:" + port + "/hello"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("SSRF protection");
    }

    @Test
    void truncatesLargeResponse() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/hello", "maxBytes", 5));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).startsWith("Hello");
        assertThat(result.content()).contains("truncated");
    }

    @Test
    void timeoutReturnsError() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/slow", "timeoutSeconds", 1));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).containsAnyOf("timed out", "timeout", "Failed to fetch");
    }

    @Test
    void missingUrlReturnsError() {
        ToolResult result = tool().execute(Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'url' is required");
    }
}
