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
package io.kairo.tools.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpenApiHttpToolTest {

    private static HttpServer server;
    private static int port;

    // Captures the last request URI seen by the server
    private static final AtomicReference<String> lastRequestUri = new AtomicReference<>();
    // Captures the last request body seen by the server
    private static final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        // Echo handler — returns 200 with JSON body describing the request
        server.createContext(
                "/",
                exchange -> {
                    lastRequestUri.set(exchange.getRequestURI().toString());
                    byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                    lastRequestBody.set(new String(bodyBytes, StandardCharsets.UTF_8));

                    byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                });

        // 404 handler at a specific path
        server.createContext(
                "/notfound",
                exchange -> {
                    lastRequestUri.set(exchange.getRequestURI().toString());
                    lastRequestBody.set("");
                    byte[] response = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(404, response.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                });

        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private OpenApiHttpTool tool() {
        return new OpenApiHttpTool(
                "http://127.0.0.1:" + port,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    @Test
    void pathParameterIsSubstituted() {
        var result =
                tool().execute(
                                "GET",
                                "/users/{userId}",
                                Map.of("userId", "42"),
                                Set.of("userId"),
                                Set.of());
        assertThat(lastRequestUri.get()).isEqualTo("/users/42");
        assertThat(result).contains("ok");
    }

    @Test
    void queryParameterIsAppended() {
        var result =
                tool().execute(
                                "GET",
                                "/search",
                                Map.of("q", "kairo", "limit", 10),
                                Set.of(),
                                Set.of("q", "limit"));
        assertThat(lastRequestUri.get()).contains("q=kairo").contains("limit=10");
        assertThat(result).contains("ok");
    }

    @Test
    void postBodyIsSentAsJson() {
        var params = Map.<String, Object>of("name", "Alice", "age", 30);
        tool().execute("POST", "/users", params, Set.of(), Set.of());
        String body = lastRequestBody.get();
        assertThat(body).contains("\"name\"").contains("Alice");
        assertThat(body).contains("\"age\"").contains("30");
    }

    @Test
    void postBodyExcludesPathAndQueryParams() {
        var params = Map.<String, Object>of("userId", "99", "include", "roles", "name", "Bob");
        tool().execute("POST", "/users/{userId}", params, Set.of("userId"), Set.of("include"));
        String body = lastRequestBody.get();
        // Only "name" is a body field; userId and include are path/query
        assertThat(body).contains("\"name\"").doesNotContain("userId").doesNotContain("include");
    }

    @Test
    void nonSuccessStatusReturnsResponseBody() {
        var result = tool().execute("GET", "/notfound", Map.of(), Set.of(), Set.of());
        // HttpClient returns body regardless of status; callers must inspect it
        assertThat(result).contains("not found");
    }

    @Test
    void timeoutReturnsErrorJson() {
        // Use a slow client timeout to simulate a connection to an unreachable host
        var timedOutTool =
                new OpenApiHttpTool(
                        "http://10.255.255.1",
                        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build());
        var result = timedOutTool.execute("GET", "/test", Map.of(), Set.of(), Set.of());
        assertThat(result).contains("error");
    }

    @Test
    void jsonResponseIsPreservedAsString() {
        var result =
                tool().execute(
                                "GET",
                                "/users/1",
                                Map.of("userId", "1"),
                                Set.of("userId"),
                                Set.of());
        assertThat(result).isEqualTo("{\"ok\":true}");
    }

    @Test
    void deleteMethodSendsNoBody() {
        tool().execute(
                        "DELETE",
                        "/users/{userId}",
                        Map.of("userId", "5"),
                        Set.of("userId"),
                        Set.of());
        assertThat(lastRequestUri.get()).isEqualTo("/users/5");
        assertThat(lastRequestBody.get()).isEmpty();
    }

    @Test
    void pathAndQueryCombined() {
        tool().execute(
                        "GET",
                        "/users/{userId}/posts",
                        Map.of("userId", "7", "page", 2),
                        Set.of("userId"),
                        Set.of("page"));
        String uri = lastRequestUri.get();
        assertThat(uri).startsWith("/users/7/posts").contains("page=2");
    }
}
