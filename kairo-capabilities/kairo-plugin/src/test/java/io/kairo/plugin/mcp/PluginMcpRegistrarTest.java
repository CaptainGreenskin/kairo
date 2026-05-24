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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.mcp.McpPlugin;
import io.kairo.api.mcp.McpPluginRegistration;
import io.kairo.api.plugin.PluginComponent;
import io.kairo.mcp.McpServerConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PluginMcpRegistrarTest {

    @Test
    void registersStdioServerViaMcpPlugin() {
        var captured = new ArrayList<McpServerConfig>();
        var stub = new StubMcpPlugin(cfg -> Mono.just(reg(cfg.name())), captured);
        var registrar = new PluginMcpRegistrar(stub);

        var component =
                new PluginComponent.McpComponent(
                        "weather", "/usr/bin/weather", List.of("--stdio"), Map.of("LOG", "1"));

        registrar.registerOne("plugin-a", component).block(Duration.ofSeconds(2));

        assertThat(captured).hasSize(1);
        var cfg = captured.get(0);
        assertThat(cfg.name()).isEqualTo("weather");
        assertThat(cfg.transportType()).isEqualTo(McpServerConfig.TransportType.STDIO);
        assertThat(cfg.command()).containsExactly("/usr/bin/weather", "--stdio");
        assertThat(cfg.env()).containsEntry("LOG", "1");
    }

    @Test
    void registerAllConcatsCommandAndArgsInOrder() {
        var captured = new ArrayList<McpServerConfig>();
        var stub = new StubMcpPlugin(cfg -> Mono.just(reg(cfg.name())), captured);
        var registrar = new PluginMcpRegistrar(stub);

        var components =
                List.of(
                        new PluginComponent.McpComponent("a", "cmdA", List.of("1", "2"), Map.of()),
                        new PluginComponent.McpComponent("b", "cmdB", List.of(), Map.of()));
        registrar.registerAll("plug", components).block(Duration.ofSeconds(2));

        assertThat(captured).extracting(McpServerConfig::name).containsExactly("a", "b");
        assertThat(captured.get(0).command()).containsExactly("cmdA", "1", "2");
        assertThat(captured.get(1).command()).containsExactly("cmdB");
    }

    @Test
    void successResetsFailureCounter() {
        AtomicInteger calls = new AtomicInteger(0);
        var stub =
                new StubMcpPlugin(
                        cfg -> {
                            int n = calls.incrementAndGet();
                            if (n == 1) return Mono.error(new RuntimeException("boom"));
                            return Mono.just(reg(cfg.name()));
                        },
                        new ArrayList<>());
        var registrar = new PluginMcpRegistrar(stub);

        var c = new PluginComponent.McpComponent("flaky", "x", List.of(), Map.of());
        // First call fails — counter should increment to 1.
        assertThatThrownBy(() -> registrar.registerOne("p", c).block(Duration.ofSeconds(2)))
                .hasMessageContaining("boom");
        assertThat(registrar.consecutiveFailures("flaky")).isEqualTo(1);
        // Second call succeeds — counter reset.
        registrar.registerOne("p", c).block(Duration.ofSeconds(2));
        assertThat(registrar.consecutiveFailures("flaky")).isZero();
    }

    @Test
    void brokenAfterThreeConsecutiveFailures() {
        var stub =
                new StubMcpPlugin(
                        cfg -> Mono.error(new RuntimeException("nope")), new ArrayList<>());
        var registrar = new PluginMcpRegistrar(stub);
        var c = new PluginComponent.McpComponent("dead", "x", List.of(), Map.of());

        for (int i = 0; i < PluginMcpRegistrar.MAX_CONSECUTIVE_FAILURES; i++) {
            assertThatThrownBy(() -> registrar.registerOne("p", c).block(Duration.ofSeconds(2)))
                    .hasMessageContaining("nope");
        }
        assertThat(registrar.consecutiveFailures("dead"))
                .isEqualTo(PluginMcpRegistrar.MAX_CONSECUTIVE_FAILURES);

        // Further attempts should short-circuit with the "broken" message rather than re-trying.
        assertThatThrownBy(() -> registrar.registerOne("p", c).block(Duration.ofSeconds(2)))
                .hasMessageContaining("marked broken");
    }

    @Test
    void disablePluginRemovesItsHandlesOnly() {
        var stub = new StubMcpPlugin(cfg -> Mono.just(reg(cfg.name())), new ArrayList<>());
        var registrar = new PluginMcpRegistrar(stub);
        var c1 = new PluginComponent.McpComponent("s1", "x", List.of(), Map.of());
        var c2 = new PluginComponent.McpComponent("s2", "y", List.of(), Map.of());

        registrar.registerOne("p1", c1).block(Duration.ofSeconds(2));
        registrar.registerOne("p2", c2).block(Duration.ofSeconds(2));

        var removed = registrar.disablePlugin("p1");
        assertThat(removed)
                .extracting(PluginMcpRegistrar.ServerHandle::serverName)
                .containsExactly("s1");
        assertThat(registrar.snapshot()).doesNotContainKey("p1");
        assertThat(registrar.snapshot()).containsKey("p2");
    }

    @Test
    void disablePluginAlsoClearsItsFailureCounters() {
        var stub = new StubMcpPlugin(cfg -> Mono.just(reg(cfg.name())), new ArrayList<>());
        var registrar = new PluginMcpRegistrar(stub);
        var c = new PluginComponent.McpComponent("temp", "x", List.of(), Map.of());

        // simulate one prior failure to leave a counter on the map
        registrar.registerOne("p", c).block(Duration.ofSeconds(2));
        registrar.disablePlugin("p");
        assertThat(registrar.consecutiveFailures("temp")).isZero();
    }

    @Test
    void registerAllStopsAtFirstFailureSoCallerCanRollback() {
        AtomicInteger calls = new AtomicInteger(0);
        var stub =
                new StubMcpPlugin(
                        cfg -> {
                            int n = calls.incrementAndGet();
                            if (n == 2) return Mono.error(new RuntimeException("second-fails"));
                            return Mono.just(reg(cfg.name()));
                        },
                        new ArrayList<>());
        var registrar = new PluginMcpRegistrar(stub);
        var components =
                List.of(
                        new PluginComponent.McpComponent("a", "cmdA", List.of(), Map.of()),
                        new PluginComponent.McpComponent("b", "cmdB", List.of(), Map.of()),
                        new PluginComponent.McpComponent("c", "cmdC", List.of(), Map.of()));

        assertThatThrownBy(
                        () ->
                                registrar
                                        .registerAll("plug", components)
                                        .block(Duration.ofSeconds(2)))
                .hasMessageContaining("second-fails");
        // Only "a" should be registered before the second fails.
        assertThat(registrar.snapshot().get("plug"))
                .extracting(PluginMcpRegistrar.ServerHandle::serverName)
                .containsExactly("a");
    }

    @Test
    void emptyComponentListIsNoOp() {
        var stub = new StubMcpPlugin(cfg -> Mono.just(reg(cfg.name())), new ArrayList<>());
        var registrar = new PluginMcpRegistrar(stub);
        registrar.registerAll("p", List.of()).block(Duration.ofSeconds(1));
        assertThat(registrar.snapshot()).isEmpty();
    }

    @Test
    void toServerConfigConvertsCorrectlyForEmptyArgsAndEnv() {
        var c = new PluginComponent.McpComponent("svr", "/bin/x", List.of(), Map.of());
        var cfg = PluginMcpRegistrar.toServerConfig(c);
        assertThat(cfg.command()).containsExactly("/bin/x");
        assertThat(cfg.env()).isEmpty();
    }

    @Test
    void toServerConfigRoutesStreamableHttpByUrl() {
        var c =
                new PluginComponent.McpComponent(
                        "remote",
                        "",
                        List.of(),
                        Map.of(),
                        PluginComponent.McpComponent.Transport.STREAMABLE_HTTP,
                        "https://api.example.com/mcp",
                        Map.of("Authorization", "Bearer xyz"));
        var cfg = PluginMcpRegistrar.toServerConfig(c);
        assertThat(cfg.transportType())
                .isEqualTo(io.kairo.mcp.McpServerConfig.TransportType.STREAMABLE_HTTP);
        assertThat(cfg.url()).isEqualTo("https://api.example.com/mcp");
        assertThat(cfg.headers()).containsEntry("Authorization", "Bearer xyz");
        assertThat(cfg.command()).isEmpty();
    }

    @Test
    void toServerConfigRoutesSse() {
        var c =
                new PluginComponent.McpComponent(
                        "events",
                        "",
                        List.of(),
                        Map.of(),
                        PluginComponent.McpComponent.Transport.SSE,
                        "https://api.example.com/events",
                        Map.of());
        var cfg = PluginMcpRegistrar.toServerConfig(c);
        assertThat(cfg.transportType()).isEqualTo(io.kairo.mcp.McpServerConfig.TransportType.SSE);
        assertThat(cfg.url()).isEqualTo("https://api.example.com/events");
    }

    @Test
    void disablePluginIsIdempotentForUnknownPluginId() {
        var stub = new StubMcpPlugin(cfg -> Mono.just(reg(cfg.name())), new ArrayList<>());
        var registrar = new PluginMcpRegistrar(stub);
        assertThat(registrar.disablePlugin("never-registered")).isEmpty();
    }

    private static McpPluginRegistration reg(String serverName) {
        return new McpPluginRegistration(serverName, List.of());
    }

    /** Captures every register invocation; pluggable success/failure response per call. */
    private static final class StubMcpPlugin implements McpPlugin {
        private final Function<McpServerConfig, Mono<McpPluginRegistration>> handler;
        private final List<McpServerConfig> captured;

        StubMcpPlugin(
                Function<McpServerConfig, Mono<McpPluginRegistration>> handler,
                List<McpServerConfig> captured) {
            this.handler = handler;
            this.captured = captured;
        }

        @Override
        public boolean supports(Object cfg) {
            return cfg instanceof McpServerConfig;
        }

        @Override
        public Mono<McpPluginRegistration> register(Object cfg) {
            McpServerConfig c = (McpServerConfig) cfg;
            captured.add(c);
            return handler.apply(c);
        }

        @Override
        public void close() {}
    }
}
