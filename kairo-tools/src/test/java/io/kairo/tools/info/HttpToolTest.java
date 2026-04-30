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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpToolTest {

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
                    exchange.getResponseHeaders().set("X-Custom-Header", "hello-value");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.createContext(
                "/echo",
                exchange -> {
                    byte[] body =
                            new String(
                                            exchange.getRequestBody().readAllBytes(),
                                            StandardCharsets.UTF_8)
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.createContext(
                "/headers",
                exchange -> {
                    String received = exchange.getRequestHeaders().getFirst("X-Test-Header");
                    byte[] body =
                            (received != null ? received : "missing")
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.createContext(
                "/error",
                exchange -> {
                    byte[] body = "Something went wrong".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(500, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.createContext(
                "/notfound",
                exchange -> {
                    byte[] body = "Not Found".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
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
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        server.createContext(
                "/large",
                exchange -> {
                    byte[] body = new byte[HttpTool.MAX_BYTES + 1_000];
                    Arrays.fill(body, (byte) 'x');
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
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
    private static HttpTool tool() {
        return new HttpTool(true);
    }

    @Test
    void get200NormalPath() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/hello"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("Hello, World!");
        assertThat(result.metadata()).containsEntry("statusCode", 200);
        assertThat(result.metadata()).containsEntry("method", "GET");
        assertThat(result.metadata()).containsEntry("url", baseUrl + "/hello");
        assertThat(result.metadata()).containsEntry("truncated", false);
        assertThat(result.metadata()).containsEntry("readOnly", false);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers =
                (Map<String, List<String>>) result.metadata().get("headers");
        assertThat(headers).isNotNull();
        assertThat(headers).containsKey("content-type");
    }

    @Test
    void postWithBody() {
        ToolResult result =
                tool().execute(
                                Map.of(
                                        "url",
                                        baseUrl + "/echo",
                                        "method",
                                        "POST",
                                        "body",
                                        "test payload"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("test payload");
        assertThat(result.metadata()).containsEntry("statusCode", 200);
        assertThat(result.metadata()).containsEntry("method", "POST");
    }

    @Test
    void customHeadersArePassedThrough() {
        ToolResult result =
                tool().execute(
                                Map.of(
                                        "url",
                                        baseUrl + "/headers",
                                        "headers",
                                        Map.of("X-Test-Header", "custom-value")));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("custom-value");
    }

    @Test
    void timeoutReturnsError() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/slow", "timeoutSeconds", 1));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("timed out");
    }

    @Test
    void non2xxReturnsErrorWithBody() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/error"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).isEqualTo("Something went wrong");
        assertThat(result.metadata()).containsEntry("statusCode", 500);
    }

    @Test
    void truncatesLargeResponse() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/large"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content().length()).isEqualTo(HttpTool.MAX_BYTES);
        assertThat(result.metadata()).containsEntry("truncated", true);
    }

    @Test
    void ssrfBlocksLocalhost() {
        HttpTool productionTool = new HttpTool();
        ToolResult result =
                productionTool.execute(Map.of("url", "http://localhost:" + port + "/hello"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("SSRF protection");
    }

    @Test
    void ssrfBlocks127() {
        HttpTool productionTool = new HttpTool();
        ToolResult result =
                productionTool.execute(Map.of("url", "http://127.0.0.1:" + port + "/hello"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("SSRF protection");
    }

    @Test
    void notFoundReturnsError() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/notfound"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).isEqualTo("Not Found");
        assertThat(result.metadata()).containsEntry("statusCode", 404);
    }

    @Test
    void invalidUrlReturnsError() {
        ToolResult result = tool().execute(Map.of("url", "ftp://example.com/resource"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("URL must start with http:// or https://");
    }

    @Test
    void missingUrlReturnsError() {
        ToolResult result = tool().execute(Map.of("method", "GET"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'url' is required");
    }

    @Test
    void unsupportedMethodDefaultsToGet() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/hello", "method", "OPTIONS"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("Hello, World!");
        assertThat(result.metadata()).containsEntry("method", "GET");
    }

    @Test
    void defaultMethodIsGet() {
        ToolResult result = tool().execute(Map.of("url", baseUrl + "/hello"));
        assertThat(result.isError()).isFalse();
        assertThat(result.metadata()).containsEntry("method", "GET");
    }
}
