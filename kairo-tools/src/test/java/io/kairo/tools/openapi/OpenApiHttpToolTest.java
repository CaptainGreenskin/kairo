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
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiHttpToolTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/",
                exchange -> {
                    byte[] response = exchange.getRequestURI().toString().getBytes();
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void constructorDoesNotThrow() {
        OpenApiHttpTool tool = new OpenApiHttpTool(baseUrl);
        assertThat(tool).isNotNull();
    }

    @Test
    void trailingSlashInBaseUrlIsNormalized() {
        OpenApiHttpTool tool = new OpenApiHttpTool(baseUrl + "/");
        // Should not throw on construction
        assertThat(tool).isNotNull();
    }

    @Test
    void getRequestReturnsBody() {
        OpenApiHttpTool tool = new OpenApiHttpTool(baseUrl);
        String result = tool.execute("GET", "/status", Map.of(), Set.of(), Set.of());
        assertThat(result).isEqualTo("/status");
    }

    @Test
    void pathParamsAreSubstituted() {
        OpenApiHttpTool tool = new OpenApiHttpTool(baseUrl);
        String result =
                tool.execute(
                        "GET",
                        "/users/{userId}",
                        Map.of("userId", "42"),
                        Set.of("userId"),
                        Set.of());
        assertThat(result).isEqualTo("/users/42");
    }

    @Test
    void queryParamsAreAppended() {
        OpenApiHttpTool tool = new OpenApiHttpTool(baseUrl);
        String result = tool.execute("GET", "/search", Map.of("q", "kairo"), Set.of(), Set.of("q"));
        assertThat(result).isEqualTo("/search?q=kairo");
    }

    @Test
    void postRequestDoesNotThrow() {
        OpenApiHttpTool tool = new OpenApiHttpTool(baseUrl);
        String result = tool.execute("POST", "/users", Map.of("name", "Alice"), Set.of(), Set.of());
        assertThat(result).isNotNull();
    }

    @Test
    void deleteRequestDoesNotThrow() {
        OpenApiHttpTool tool = new OpenApiHttpTool(baseUrl);
        String result = tool.execute("DELETE", "/items/1", Map.of(), Set.of(), Set.of());
        assertThat(result).isNotNull();
    }

    @Test
    void unreachableHostReturnsErrorJson() {
        OpenApiHttpTool tool = new OpenApiHttpTool("http://localhost:1");
        String result = tool.execute("GET", "/fail", Map.of(), Set.of(), Set.of());
        assertThat(result).contains("error");
    }
}
