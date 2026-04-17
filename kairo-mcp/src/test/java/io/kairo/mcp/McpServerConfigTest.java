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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerConfigTest {

    @Test
    void stdioConfigCreation() {
        McpServerConfig config = McpServerConfig.stdio("fs", "npx", "-y", "server-fs");
        assertEquals("fs", config.name());
        assertEquals(McpServerConfig.TransportType.STDIO, config.transportType());
        assertEquals(List.of("npx", "-y", "server-fs"), config.command());
        assertNull(config.url());
        assertEquals(Duration.ofSeconds(30), config.requestTimeout());
    }

    @Test
    void streamableHttpConfigCreation() {
        McpServerConfig config = McpServerConfig.streamableHttp("api", "http://localhost:8080/mcp");
        assertEquals("api", config.name());
        assertEquals(McpServerConfig.TransportType.STREAMABLE_HTTP, config.transportType());
        assertEquals("http://localhost:8080/mcp", config.url());
        assertTrue(config.command().isEmpty());
    }

    @Test
    void sseConfigCreation() {
        McpServerConfig config = McpServerConfig.sse("events", "http://localhost:9090/sse");
        assertEquals("events", config.name());
        assertEquals(McpServerConfig.TransportType.SSE, config.transportType());
        assertEquals("http://localhost:9090/sse", config.url());
    }

    @Test
    void builderWithHeaders() {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("api")
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url("http://localhost:8080")
                        .headers(Map.of("Authorization", "Bearer token123"))
                        .build();
        assertEquals("Bearer token123", config.headers().get("Authorization"));
    }

    @Test
    void builderWithEnvVars() {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("fs")
                        .transportType(McpServerConfig.TransportType.STDIO)
                        .command(List.of("npx", "server"))
                        .env(Map.of("HOME", "/tmp"))
                        .build();
        assertEquals("/tmp", config.env().get("HOME"));
    }

    @Test
    void builderWithEnableDisableTools() {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("tools")
                        .transportType(McpServerConfig.TransportType.STDIO)
                        .command(List.of("cmd"))
                        .enableTools(List.of("read", "write"))
                        .disableTools(List.of("delete"))
                        .build();
        assertEquals(List.of("read", "write"), config.enableTools());
        assertEquals(List.of("delete"), config.disableTools());
    }

    @Test
    void builderRequiresName() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        McpServerConfig.builder()
                                .transportType(McpServerConfig.TransportType.STDIO)
                                .build());
    }

    @Test
    void builderRequiresTransportType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> McpServerConfig.builder().name("test").build());
    }

    @Test
    void builderWithBearerToken() {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("api")
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url("http://localhost:8080")
                        .bearerToken("my-secret-token")
                        .build();
        assertEquals("my-secret-token", config.bearerToken());
    }

    @Test
    void builderWithQueryParams() {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("api")
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url("http://localhost:8080")
                        .queryParams(Map.of("api_key", "abc123", "version", "v2"))
                        .build();
        assertEquals("abc123", config.queryParams().get("api_key"));
        assertEquals("v2", config.queryParams().get("version"));
    }

    @Test
    void builderWithBearerTokenAndCustomHeaders() {
        McpServerConfig config =
                McpServerConfig.builder()
                        .name("api")
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url("http://localhost:8080")
                        .bearerToken("token123")
                        .headers(Map.of("X-Custom", "value"))
                        .build();
        assertEquals("token123", config.bearerToken());
        assertEquals("value", config.headers().get("X-Custom"));
    }

    @Test
    void staticFactoryConfigsHaveEmptyQueryParams() {
        McpServerConfig stdio = McpServerConfig.stdio("fs", "cmd");
        McpServerConfig http = McpServerConfig.streamableHttp("api", "http://localhost");
        McpServerConfig sse = McpServerConfig.sse("events", "http://localhost");

        assertTrue(stdio.queryParams().isEmpty());
        assertNull(stdio.bearerToken());
        assertTrue(http.queryParams().isEmpty());
        assertNull(http.bearerToken());
        assertTrue(sse.queryParams().isEmpty());
        assertNull(sse.bearerToken());
    }
}
