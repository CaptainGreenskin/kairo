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
package io.kairo.mcp;

import static org.junit.jupiter.api.Assertions.*;

import io.modelcontextprotocol.client.McpAsyncClient;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpClientBuilderTest {

    @Test
    void createRequiresNonNullName() {
        assertThrows(IllegalArgumentException.class, () -> McpClientBuilder.create(null));
    }

    @Test
    void createRequiresNonBlankName() {
        assertThrows(IllegalArgumentException.class, () -> McpClientBuilder.create("  "));
    }

    @Test
    void buildRequiresTransport() {
        McpClientBuilder builder = McpClientBuilder.create("test");
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void stdioTransportCreatesClient() {
        McpAsyncClient client =
                McpClientBuilder.create("fs").stdioTransport("echo", "hello").build();
        assertNotNull(client);
    }

    @Test
    void streamableHttpTransportCreatesClient() {
        McpAsyncClient client =
                McpClientBuilder.create("api")
                        .streamableHttpTransport("http://localhost:8080/mcp")
                        .build();
        assertNotNull(client);
    }

    @Test
    void sseTransportCreatesClient() {
        McpAsyncClient client =
                McpClientBuilder.create("events").sseTransport("http://localhost:9090/sse").build();
        assertNotNull(client);
    }

    @Test
    void requestTimeoutIsApplied() {
        // Verify builder accepts custom timeout without error
        McpAsyncClient client =
                McpClientBuilder.create("fs")
                        .stdioTransport("echo")
                        .requestTimeout(Duration.ofSeconds(120))
                        .build();
        assertNotNull(client);
    }

    @Test
    void fromConfigStdio() {
        McpServerConfig config = McpServerConfig.stdio("fs", "npx", "-y", "server-fs");
        McpAsyncClient client = McpClientBuilder.fromConfig(config).build();
        assertNotNull(client);
    }

    @Test
    void fromConfigStreamableHttp() {
        McpServerConfig config = McpServerConfig.streamableHttp("api", "http://localhost:8080");
        McpAsyncClient client = McpClientBuilder.fromConfig(config).build();
        assertNotNull(client);
    }

    @Test
    void fromConfigSse() {
        McpServerConfig config = McpServerConfig.sse("events", "http://localhost:9090");
        McpAsyncClient client = McpClientBuilder.fromConfig(config).build();
        assertNotNull(client);
    }

    @Test
    void fromConfigStdioRequiresCommand() {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("empty")
                        .transportType(McpServerConfig.TransportType.STDIO)
                        .command(List.of())
                        .build();
        assertThrows(IllegalArgumentException.class, () -> McpClientBuilder.fromConfig(config));
    }

    @Test
    void bearerTokenSetsAuthorizationHeader() throws Exception {
        McpClientBuilder builder =
                McpClientBuilder.create("api")
                        .streamableHttpTransport("http://localhost:8080")
                        .bearerToken("my-token");
        Map<String, String> headers = getHttpHeaders(builder);
        assertEquals("Bearer my-token", headers.get("Authorization"));
    }

    @Test
    void fromConfigBearerTokenSetsAuthorizationHeader() throws Exception {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("api")
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url("http://localhost:8080")
                        .bearerToken("secret-token")
                        .build();
        McpClientBuilder builder = McpClientBuilder.fromConfig(config);
        Map<String, String> headers = getHttpHeaders(builder);
        assertEquals("Bearer secret-token", headers.get("Authorization"));
    }

    @Test
    void customHeaderOverridesBearerToken() throws Exception {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("api")
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url("http://localhost:8080")
                        .bearerToken("token-from-bearer")
                        .headers(Map.of("Authorization", "Basic custom-auth"))
                        .build();
        McpClientBuilder builder = McpClientBuilder.fromConfig(config);
        Map<String, String> headers = getHttpHeaders(builder);
        // Explicit headers should override bearer token
        assertEquals("Basic custom-auth", headers.get("Authorization"));
    }

    @Test
    void queryParamsApplied() throws Exception {
        McpClientBuilder builder =
                McpClientBuilder.create("api")
                        .streamableHttpTransport("http://localhost:8080")
                        .queryParam("api_key", "abc123")
                        .queryParam("version", "v2");
        Map<String, String> params = getHttpQueryParams(builder);
        assertEquals("abc123", params.get("api_key"));
        assertEquals("v2", params.get("version"));
    }

    @Test
    void fromConfigQueryParamsApplied() throws Exception {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("api")
                        .transportType(McpServerConfig.TransportType.SSE)
                        .url("http://localhost:9090")
                        .queryParams(Map.of("token", "xyz"))
                        .build();
        McpClientBuilder builder = McpClientBuilder.fromConfig(config);
        Map<String, String> params = getHttpQueryParams(builder);
        assertEquals("xyz", params.get("token"));
    }

    @Test
    void bearerTokenAndCustomHeadersCoexist() throws Exception {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("api")
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url("http://localhost:8080")
                        .bearerToken("my-token")
                        .headers(Map.of("X-Custom", "value"))
                        .build();
        McpClientBuilder builder = McpClientBuilder.fromConfig(config);
        Map<String, String> headers = getHttpHeaders(builder);
        assertEquals("Bearer my-token", headers.get("Authorization"));
        assertEquals("value", headers.get("X-Custom"));
    }

    @Test
    void fromConfigBearerTokenWithQueryParams() {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("api")
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url("http://localhost:8080")
                        .bearerToken("token")
                        .queryParams(Map.of("version", "v1"))
                        .build();
        // Should build without error
        McpAsyncClient client = McpClientBuilder.fromConfig(config).build();
        assertNotNull(client);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getHttpHeaders(McpClientBuilder builder) throws Exception {
        Field field = McpClientBuilder.class.getDeclaredField("httpHeaders");
        field.setAccessible(true);
        return (Map<String, String>) field.get(builder);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getHttpQueryParams(McpClientBuilder builder) throws Exception {
        Field field = McpClientBuilder.class.getDeclaredField("httpQueryParams");
        field.setAccessible(true);
        return (Map<String, String>) field.get(builder);
    }
}
