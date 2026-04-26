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
import org.junit.jupiter.api.Test;

class McpServerConfigTest {

    @Test
    void stdio_factory_setsTransportAndCommand() {
        McpServerConfig cfg = McpServerConfig.stdio("test-server", "npx", "-y", "pkg");
        assertEquals("test-server", cfg.name());
        assertEquals(McpServerConfig.TransportType.STDIO, cfg.transportType());
        assertEquals(List.of("npx", "-y", "pkg"), cfg.command());
    }

    @Test
    void stdio_factory_defaults() {
        McpServerConfig cfg = McpServerConfig.stdio("s", "cmd");
        assertEquals(Duration.ofSeconds(30), cfg.requestTimeout());
        assertEquals(McpServerConfig.DEFAULT_MAX_TOOLS_PER_SERVER, cfg.maxToolsPerServer());
        assertEquals(McpServerConfig.DEFAULT_MAX_CONCURRENT_CALLS, cfg.maxConcurrentCalls());
        assertTrue(cfg.schemaValidation());
    }

    @Test
    void streamableHttp_factory_setsUrl() {
        McpServerConfig cfg = McpServerConfig.streamableHttp("s", "https://api.example.com/mcp");
        assertEquals(McpServerConfig.TransportType.STREAMABLE_HTTP, cfg.transportType());
        assertEquals("https://api.example.com/mcp", cfg.url());
    }

    @Test
    void sse_factory_setsUrl() {
        McpServerConfig cfg = McpServerConfig.sse("s", "https://api.example.com/sse");
        assertEquals(McpServerConfig.TransportType.SSE, cfg.transportType());
        assertEquals("https://api.example.com/sse", cfg.url());
    }

    @Test
    void builder_basicBuild() {
        McpServerConfig cfg =
                McpServerConfig.builder()
                        .name("builder-server")
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url("https://example.com")
                        .build();
        assertEquals("builder-server", cfg.name());
        assertEquals(McpServerConfig.TransportType.STREAMABLE_HTTP, cfg.transportType());
    }

    @Test
    void builder_nullNameThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        McpServerConfig.builder()
                                .transportType(McpServerConfig.TransportType.STDIO)
                                .build());
    }

    @Test
    void builder_nullTransportTypeThrows() {
        assertThrows(
                IllegalArgumentException.class, () -> McpServerConfig.builder().name("s").build());
    }

    @Test
    void defaultConstants_arePositive() {
        assertTrue(McpServerConfig.DEFAULT_MAX_TOOLS_PER_SERVER > 0);
        assertTrue(McpServerConfig.DEFAULT_MAX_CONCURRENT_CALLS > 0);
    }

    @Test
    void transportType_threeValues() {
        assertEquals(3, McpServerConfig.TransportType.values().length);
    }

    @Test
    void builder_schemaValidationCanBeDisabled() {
        McpServerConfig cfg =
                McpServerConfig.builder()
                        .name("s")
                        .transportType(McpServerConfig.TransportType.STDIO)
                        .schemaValidation(false)
                        .build();
        assertFalse(cfg.schemaValidation());
    }
}
