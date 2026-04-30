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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WebSearchTool}.
 *
 * <p>Uses the package-private test constructor to inject a local {@link HttpServer} URL and a plain
 * {@link HttpClient}, avoiding any need for HTTPS or proxy configuration.
 */
class WebSearchToolTest {

    private static HttpServer server;
    private static String serverUrl;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicReference<String> scenario = new AtomicReference<>("normal");

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        serverUrl = "http://127.0.0.1:" + port + "/search";

        server.createContext(
                "/search",
                exchange -> {
                    byte[] requestBytes;
                    try (InputStream is = exchange.getRequestBody()) {
                        requestBytes = is.readAllBytes();
                    }

                    // Parse max_results from the request body so the mock respects it
                    int requestedMax = 5;
                    try {
                        com.fasterxml.jackson.databind.JsonNode req = MAPPER.readTree(requestBytes);
                        com.fasterxml.jackson.databind.JsonNode mr = req.get("max_results");
                        if (mr != null && mr.isInt()) requestedMax = mr.intValue();
                    } catch (Exception ignored) {
                    }

                    final int maxForResponse = requestedMax;
                    String currentScenario = scenario.get();

                    switch (currentScenario) {
                        case "unauthorized" -> {
                            byte[] body =
                                    "{\"error\":\"Invalid API key\"}"
                                            .getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(401, body.length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(body);
                            }
                        }
                        case "ratelimit" -> {
                            byte[] body =
                                    "{\"error\":\"Rate limit exceeded\"}"
                                            .getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(429, body.length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(body);
                            }
                        }
                        case "nonjson" -> {
                            byte[] body = "This is not JSON".getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().set("Content-Type", "text/plain");
                            exchange.sendResponseHeaders(200, body.length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(body);
                            }
                        }
                        case "slow" -> {
                            try {
                                Thread.sleep(15_000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            byte[] body = "late".getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(200, body.length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(body);
                            }
                        }
                        case "empty" -> {
                            byte[] out = MAPPER.writeValueAsBytes(buildEmptyResponse());
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(200, out.length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(out);
                            }
                        }
                        default -> {
                            byte[] out =
                                    MAPPER.writeValueAsBytes(
                                            buildNormalResponse(maxForResponse, true));
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(200, out.length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(out);
                            }
                        }
                    }
                });

        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void setUp() {
        scenario.set("normal");
    }

    @AfterEach
    void tearDown() {
        scenario.set("normal");
    }

    private WebSearchTool tool() {
        return new WebSearchTool(serverUrl, HttpClient.newHttpClient());
    }

    private WebSearchTool toolWithTimeout(Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        return new WebSearchTool(serverUrl, client);
    }

    private static ToolContext toolContext(String apiKey) {
        return new ToolContext(
                "test-agent",
                "test-session",
                Map.of("TAVILY_API_KEY", apiKey),
                null,
                TenantContext.SINGLE);
    }

    private static ObjectNode buildNormalResponse(int maxResults, boolean includeAnswer) {
        ObjectNode resp = MAPPER.createObjectNode();
        if (includeAnswer) {
            resp.put("answer", "This is the AI-generated answer.");
        }
        ArrayNode results = resp.putArray("results");
        for (int i = 0; i < maxResults; i++) {
            ObjectNode r = results.addObject();
            r.put("title", "Result Title " + (i + 1));
            r.put("url", "https://example.com/result" + (i + 1));
            r.put("content", "Content snippet for result " + (i + 1) + ".");
        }
        return resp;
    }

    private static ObjectNode buildEmptyResponse() {
        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("answer", "");
        resp.putArray("results");
        return resp;
    }

    @Test
    void normalSearchReturnsResults() {
        ToolResult result =
                tool().execute(Map.of("query", "Java 21 features"), toolContext("test-key"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Answer");
        assertThat(result.content()).contains("AI-generated answer");
        assertThat(result.content()).contains("Results");
        assertThat(result.metadata()).containsEntry("query", "Java 21 features");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, String>> results =
                (java.util.List<Map<String, String>>) result.metadata().get("results");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0)).containsKeys("title", "url", "snippet");
    }

    @Test
    void queryMissingReturnsError() {
        ToolResult result = tool().execute(Map.of(), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("query");
    }

    @Test
    void queryBlankReturnsError() {
        ToolResult result = tool().execute(Map.of("query", "   "), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("query");
    }

    @Test
    void apiKeyNotConfiguredReturnsError() {
        WebSearchTool noCtxTool = new WebSearchTool(serverUrl, HttpClient.newHttpClient());
        ToolResult result = noCtxTool.execute(Map.of("query", "test"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("TAVILY_API_KEY");
    }

    @Test
    void apiKeyBlankReturnsError() {
        ToolResult result = tool().execute(Map.of("query", "test"), toolContext("   "));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("TAVILY_API_KEY");
    }

    @Test
    void unauthorizedReturnsError() {
        scenario.set("unauthorized");
        ToolResult result = tool().execute(Map.of("query", "test"), toolContext("invalid-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("401");
    }

    @Test
    void rateLimitReturnsErrorWithStatusCode() {
        scenario.set("ratelimit");
        ToolResult result = tool().execute(Map.of("query", "test"), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("429");
    }

    @Test
    void emptyResultsReturnsNotErrorWithNoResults() {
        scenario.set("empty");
        ToolResult result =
                tool().execute(Map.of("query", "obscure query"), toolContext("test-key"));
        assertThat(result.isError()).isFalse();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, String>> results =
                (java.util.List<Map<String, String>>) result.metadata().get("results");
        assertThat(results).isEmpty();
        assertThat(result.metadata()).containsEntry("resultCount", 0);
    }

    @Test
    void maxResultsLimitsResultCount() {
        ToolResult result =
                tool().execute(
                                Map.of("query", "test query", "maxResults", 2),
                                toolContext("test-key"));
        assertThat(result.isError()).isFalse();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, String>> results =
                (java.util.List<Map<String, String>>) result.metadata().get("results");
        assertThat(results).hasSize(2);
    }

    @Test
    void searchResultsCorrectlyParseTitleUrlContent() {
        ToolResult result = tool().execute(Map.of("query", "test"), toolContext("test-key"));
        assertThat(result.isError()).isFalse();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, String>> results =
                (java.util.List<Map<String, String>>) result.metadata().get("results");
        assertThat(results).isNotEmpty();
        Map<String, String> first = results.get(0);
        assertThat(first.get("title")).isEqualTo("Result Title 1");
        assertThat(first.get("url")).isEqualTo("https://example.com/result1");
        assertThat(first.get("snippet")).isEqualTo("Content snippet for result 1.");
    }

    @Test
    void nonJsonResponseReturnsError() {
        scenario.set("nonjson");
        ToolResult result = tool().execute(Map.of("query", "test"), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("failed");
    }

    @Test
    void searchUsesToolContextApiKey() {
        ToolResult result =
                tool().execute(Map.of("query", "context key test"), toolContext("ctx-api-key"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Answer");
    }

    @Test
    void timeoutReturnsError() {
        scenario.set("slow");
        ToolResult result =
                toolWithTimeout(Duration.ofMillis(300))
                        .execute(Map.of("query", "slow query"), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Tavily API call failed");
    }
}
