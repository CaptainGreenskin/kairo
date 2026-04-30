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
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class WebSearchToolTest {

    private static HttpServer server;
    private static int port;
    private static String baseUrl;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ORIGINAL_TAVILY_URL = "https://api.tavily.com/search";

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;

        // Normal search with answer and results
        server.createContext(
                "/search",
                exchange -> {
                    byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                    String body = new String(bodyBytes, StandardCharsets.UTF_8);

                    ObjectNode req = (ObjectNode) MAPPER.readTree(body);
                    int maxResults = req.path("max_results").asInt(5);
                    boolean includeAnswer = req.path("include_answer").asBoolean(true);

                    ObjectNode resp = MAPPER.createObjectNode();
                    if (includeAnswer) {
                        resp.put("answer", "This is the AI-generated answer.");
                    }

                    ArrayNode results = resp.putArray("results");
                    for (int i = 0; i < Math.min(maxResults, 3); i++) {
                        ObjectNode r = results.addObject();
                        r.put("title", "Result Title " + (i + 1));
                        r.put("url", "https://example.com/result" + (i + 1));
                        r.put("content", "Content snippet for result " + (i + 1) + ".");
                    }

                    byte[] out = MAPPER.writeValueAsBytes(resp);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, out.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(out);
                    }
                });

        // Empty results
        server.createContext(
                "/empty",
                exchange -> {
                    ObjectNode resp = MAPPER.createObjectNode();
                    resp.put("answer", "");
                    resp.putArray("results");
                    byte[] out = MAPPER.writeValueAsBytes(resp);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, out.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(out);
                    }
                });

        // 401 Unauthorized
        server.createContext(
                "/unauthorized",
                exchange -> {
                    byte[] body =
                            "{\"error\":\"Invalid API key\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(401, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        // 429 Rate limit
        server.createContext(
                "/ratelimit",
                exchange -> {
                    byte[] body =
                            "{\"error\":\"Rate limit exceeded\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(429, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        // Non-JSON response
        server.createContext(
                "/nonjson",
                exchange -> {
                    byte[] body = "This is not JSON".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                });

        // Slow endpoint for timeout testing
        server.createContext(
                "/slow",
                exchange -> {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    byte[] body = "late".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
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

    @BeforeEach
    void setUp() {
        setStaticField(WebSearchTool.class, "TAVILY_URL", baseUrl + "/search");
    }

    @AfterEach
    void tearDown() {
        setStaticField(WebSearchTool.class, "TAVILY_URL", ORIGINAL_TAVILY_URL);
    }

    /** Creates a ToolContext with TAVILY_API_KEY injected. */
    private static ToolContext toolContext(String apiKey) {
        return new ToolContext(
                "test-agent",
                "test-session",
                Map.of("TAVILY_API_KEY", apiKey),
                null,
                TenantContext.SINGLE);
    }

    /** Uses Unsafe to set a static final field for testing. */
    @SuppressWarnings("deprecation")
    private static void setStaticField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            long offset = unsafe.staticFieldOffset(field);
            unsafe.putObject(clazz, offset, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set static field: " + fieldName, e);
        }
    }

    /** Uses reflection to set an instance field for testing. */
    private static void setInstanceField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            long offset = unsafe.objectFieldOffset(field);
            unsafe.putObject(target, offset, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set instance field: " + fieldName, e);
        }
    }

    @Test
    void normalSearchReturnsResults() {
        WebSearchTool tool = new WebSearchTool();
        ToolResult result =
                tool.execute(Map.of("query", "Java 21 features"), toolContext("test-key"));
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
        WebSearchTool tool = new WebSearchTool();
        ToolResult result = tool.execute(Map.of(), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("query");
    }

    @Test
    void queryBlankReturnsError() {
        WebSearchTool tool = new WebSearchTool();
        ToolResult result = tool.execute(Map.of("query", "   "), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("query");
    }

    @Test
    void apiKeyNotConfiguredReturnsError() {
        WebSearchTool tool = new WebSearchTool();
        ToolResult result = tool.execute(Map.of("query", "test"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("TAVILY_API_KEY");
    }

    @Test
    void apiKeyBlankReturnsError() {
        WebSearchTool tool = new WebSearchTool();
        ToolContext ctx = toolContext("   ");
        ToolResult result = tool.execute(Map.of("query", "test"), ctx);
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("TAVILY_API_KEY");
    }

    @Test
    void unauthorizedReturnsError() {
        setStaticField(WebSearchTool.class, "TAVILY_URL", baseUrl + "/unauthorized");
        WebSearchTool tool = new WebSearchTool();
        ToolResult result = tool.execute(Map.of("query", "test"), toolContext("invalid-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("401");
    }

    @Test
    void rateLimitReturnsErrorWithStatusCode() {
        setStaticField(WebSearchTool.class, "TAVILY_URL", baseUrl + "/ratelimit");
        WebSearchTool tool = new WebSearchTool();
        ToolResult result = tool.execute(Map.of("query", "test"), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("429");
    }

    @Test
    void emptyResultsReturnsNotErrorWithNoResults() {
        setStaticField(WebSearchTool.class, "TAVILY_URL", baseUrl + "/empty");
        WebSearchTool tool = new WebSearchTool();
        ToolResult result = tool.execute(Map.of("query", "obscure query"), toolContext("test-key"));
        assertThat(result.isError()).isFalse();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, String>> results =
                (java.util.List<Map<String, String>>) result.metadata().get("results");
        assertThat(results).isEmpty();
        assertThat(result.metadata()).containsEntry("resultCount", 0);
    }

    @Test
    void maxResultsLimitsResultCount() {
        WebSearchTool tool = new WebSearchTool();
        ToolResult result =
                tool.execute(
                        Map.of("query", "test query", "maxResults", 2), toolContext("test-key"));
        assertThat(result.isError()).isFalse();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, String>> results =
                (java.util.List<Map<String, String>>) result.metadata().get("results");
        assertThat(results).hasSize(2);
    }

    @Test
    void searchResultsCorrectlyParseTitleUrlContent() {
        WebSearchTool tool = new WebSearchTool();
        ToolResult result = tool.execute(Map.of("query", "test"), toolContext("test-key"));
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
        setStaticField(WebSearchTool.class, "TAVILY_URL", baseUrl + "/nonjson");
        WebSearchTool tool = new WebSearchTool();
        ToolResult result = tool.execute(Map.of("query", "test"), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("failed");
    }

    @Test
    void searchUsesToolContextApiKey() {
        WebSearchTool tool = new WebSearchTool();
        ToolResult result =
                tool.execute(Map.of("query", "context key test"), toolContext("ctx-api-key"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Answer");
    }

    @Test
    void timeoutReturnsError() {
        setStaticField(WebSearchTool.class, "TAVILY_URL", baseUrl + "/slow");
        HttpClient shortTimeoutClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
        WebSearchTool tool = new WebSearchTool();
        setInstanceField(tool, "httpClient", shortTimeoutClient);
        ToolResult result = tool.execute(Map.of("query", "slow query"), toolContext("test-key"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Tavily API call failed");
    }
}
