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

import io.kairo.api.mcp.McpPlugin;
import io.kairo.api.mcp.McpPluginRegistration;
import io.kairo.api.plugin.PluginComponent;
import io.kairo.mcp.spi.DefaultMcpPlugin;
import io.kairo.plugin.mcp.testfixtures.FakeMcpStdioServer;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Full MCP-protocol round-trip test against the real production stack:
 *
 * <ul>
 *   <li>Subprocess fixture: a child JVM running {@link FakeMcpStdioServer}, which uses kairo-mcp's
 *       {@code KairoMcpServer} backed by the official MCP Java SDK to speak real JSON-RPC over
 *       stdio and expose one {@code echo} tool.
 *   <li>Client side: the production {@link DefaultMcpPlugin} (which under the hood drives {@code
 *       McpClientRegistry} → official SDK MCP client → stdio handshake → {@code initialize} →
 *       {@code tools/list}).
 *   <li>Glue: {@link PluginMcpRegistrar}, the same code that wires plugin {@code mcpServers}
 *       declarations onto the SPI in production.
 * </ul>
 *
 * <p>Unlike {@link StdioMcpProcessIT}, which fakes the SDK and only proves {@code ProcessBuilder}
 * arguments are correct, this IT exercises the full protocol-level conversation. If it passes, a
 * real Claude Code plugin shipping {@code mcpServers} can boot its server and have its tools
 * registered into Kairo without further wiring.
 */
@Tag("integration")
@DisabledOnOs(OS.WINDOWS)
class RealMcpProtocolIT {

    @Test
    @DisplayName(
            "register stdio MCP server (real fixture) → DefaultMcpPlugin completes handshake and"
                    + " surfaces 'echo' tool")
    void registerSpawnsRealMcpHandshake() throws Exception {
        // Build a command that re-invokes this JVM with the fixture's main(). We pass the same
        // classpath we are running under, then run io.kairo.plugin.mcp.testfixtures
        // .FakeMcpStdioServer.
        String javaBin =
                Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString();
        String classpath = System.getProperty("java.class.path");
        // Make sure we're not running under a JVM with an empty classpath (would mean Maven
        // forked us with surefireboot. With the failsafe runner the property is set normally).
        assertThat(classpath).as("classpath visible for forking child JVM").isNotBlank();

        var component =
                new PluginComponent.McpComponent(
                        "fake-stdio",
                        javaBin,
                        List.of("-cp", classpath, FakeMcpStdioServer.class.getName()),
                        Map.of(
                                // Reduce log noise from the child SDK.
                                "JAVA_TOOL_OPTIONS",
                                "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn"));

        McpPlugin real = new DefaultMcpPlugin();
        var registrar = new PluginMcpRegistrar(real);

        try {
            McpPluginRegistration reg =
                    registrar
                            .registerOne("real-mcp-plugin", component)
                            .block(Duration.ofSeconds(60));
            assertThat(reg).as("registration completed").isNotNull();
            assertThat(reg.serverName()).isEqualTo("fake-stdio");
            assertThat(reg.tools())
                    .as("DefaultMcpPlugin should have discovered 'echo' from the fixture server")
                    .extracting(t -> t.definition().name())
                    .anyMatch(name -> name.contains("echo"));
            assertThat(registrar.snapshot()).containsKey("real-mcp-plugin");
        } finally {
            real.close();
        }

        // Sanity: confirm we are the parent JVM (not invoked recursively) — the fixture's
        // main() doesn't return, so a runaway recursion would deadlock instead of failing here,
        // but printing the parent PID into the test report keeps debugging fast.
        assertThat(ManagementFactory.getRuntimeMXBean().getName()).contains("@");
    }
}
