/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.plugin.PluginComponent;
import io.kairo.mcp.spi.DefaultMcpPlugin;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Round-trip wire-level test for the remote MCP transports. We don't reimplement a full MCP HTTP
 * server here (the SDK ships one for prod use); instead we stand up an {@link MockWebServer}, point
 * the production {@link DefaultMcpPlugin} at it via the same {@link PluginMcpRegistrar} path the
 * Plugin SPI uses, and verify the SDK <em>actually opens an HTTP connection</em> against the URL
 * declared in the plugin's {@code mcpServers} entry.
 *
 * <p>That answers the wire question "does the loader → registrar → SDK chain really make a network
 * call for {@code type:streamable_http} and {@code type:sse}, or does it silently drop on the
 * floor?" — without dragging in Jetty / servlet machinery in the test class-path.
 *
 * <p>Caveat: the registration itself will ultimately fail (MockWebServer answers with a stub
 * payload that isn't valid MCP JSON-RPC), but the test only cares that the connection was
 * attempted, which is the contract under our control.
 */
@Tag("integration")
class RemoteMcpTransportIT {

    private MockWebServer server;
    private DefaultMcpPlugin mcpPlugin;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        // Answer every request with something innocuous so the SDK doesn't hang the test thread
        // — but the first request is all we assert on.
        for (int i = 0; i < 5; i++) {
            server.enqueue(
                    new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{}"));
        }
        server.start();
        mcpPlugin = new DefaultMcpPlugin();
    }

    @AfterEach
    void stop() throws Exception {
        try {
            mcpPlugin.close();
        } catch (Exception ignored) {
            // shut-down of half-initialised clients can throw; we don't care here.
        }
        server.shutdown();
    }

    @Test
    @DisplayName(
            "STREAMABLE_HTTP plugin component → DefaultMcpPlugin makes an HTTP request to the declared URL")
    void streamableHttpComponentReachesNetwork() throws Exception {
        var component =
                new PluginComponent.McpComponent(
                        "remote-http",
                        "",
                        List.of(),
                        Map.of(),
                        PluginComponent.McpComponent.Transport.STREAMABLE_HTTP,
                        server.url("/mcp").toString(),
                        Map.of("X-Kairo-Test", "yes"));

        var registrar = new PluginMcpRegistrar(mcpPlugin);
        // Don't block — the SDK will eventually error because MockWebServer doesn't speak MCP.
        // We only care that the connection attempt happens, which the SDK does eagerly during
        // initialize().
        registrar
                .registerOne("plugin-a", component)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(err -> reactor.core.publisher.Mono.empty())
                .subscribe();

        RecordedRequest first = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(first).as("SDK must have opened an HTTP connection").isNotNull();
        assertThat(first.getPath()).startsWith("/mcp");
        assertThat(first.getHeader("X-Kairo-Test"))
                .as("plugin-declared headers must propagate to the wire")
                .isEqualTo("yes");
    }

    @Test
    @DisplayName(
            "SSE plugin component → DefaultMcpPlugin makes an HTTP request to the declared URL")
    void sseComponentReachesNetwork() throws Exception {
        var component =
                new PluginComponent.McpComponent(
                        "remote-sse",
                        "",
                        List.of(),
                        Map.of(),
                        PluginComponent.McpComponent.Transport.SSE,
                        server.url("/events").toString(),
                        Map.of("Authorization", "Bearer test-token"));

        var registrar = new PluginMcpRegistrar(mcpPlugin);
        registrar
                .registerOne("plugin-b", component)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(err -> reactor.core.publisher.Mono.empty())
                .subscribe();

        RecordedRequest first = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(first).as("SDK must have opened an HTTP connection for SSE").isNotNull();
        // SDK convention: SSE base URL gets `/sse` appended for the event stream endpoint.
        assertThat(first.getPath()).isEqualTo("/sse");
        assertThat(first.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }
}
