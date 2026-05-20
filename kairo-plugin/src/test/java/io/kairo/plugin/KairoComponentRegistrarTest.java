/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.mcp.McpPlugin;
import io.kairo.api.mcp.McpPluginRegistration;
import io.kairo.api.plugin.PluginComponent;
import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.plugin.mcp.PluginMcpRegistrar;
import io.kairo.plugin.testsupport.Fixtures;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class KairoComponentRegistrarTest {

    @Test
    void registersSkillsViaSkillRegistry() throws Exception {
        var skillReg = new RecordingSkillRegistry();
        var env = new PluginEnvironment();
        var registrar = new KairoComponentRegistrar(skillReg, null, env);

        Path root = Fixtures.copyToTemp("full-plugin");
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.SkillComponent(
                                "greet", root.resolve("skills/greet/SKILL.md"), "ns"));
        registrar.registerAll("p1", components).block(Duration.ofSeconds(2));

        assertThat(skillReg.loadedFiles).hasSize(1);
        assertThat(registrar.registeredCount("p1")).isEqualTo(1);
    }

    @Test
    void commandsAreRegisteredAsFlatSkills() throws Exception {
        var skillReg = new RecordingSkillRegistry();
        var registrar = new KairoComponentRegistrar(skillReg, null, null);

        Path root = Fixtures.copyToTemp("full-plugin");
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.CommandComponent(
                                "quick-greet", root.resolve("commands/quick-greet.md"), "ns"));
        registrar.registerAll("p1", components).block(Duration.ofSeconds(2));

        assertThat(skillReg.loadedFiles).hasSize(1);
    }

    @Test
    void registersBinDirInPluginEnvironment() throws Exception {
        var env = new PluginEnvironment();
        var registrar = new KairoComponentRegistrar(null, null, env);

        Path binFile = Fixtures.copyToTemp("full-plugin").resolve("bin/hello.sh");
        var components =
                List.<PluginComponent>of(new PluginComponent.BinComponent("hello.sh", binFile));
        registrar.registerAll("p1", components).block(Duration.ofSeconds(2));

        assertThat(env.activeBinDirs()).hasSize(1);
    }

    @Test
    void registersMcpViaPluginMcpRegistrar() {
        var mcpStub = new StubMcpPlugin();
        var registrar = new KairoComponentRegistrar(null, new PluginMcpRegistrar(mcpStub), null);

        var components =
                List.<PluginComponent>of(
                        new PluginComponent.McpComponent(
                                "weather", "/usr/bin/weather", List.of("--stdio"), Map.of()));
        registrar.registerAll("p1", components).block(Duration.ofSeconds(2));

        assertThat(mcpStub.registeredCount.get()).isEqualTo(1);
    }

    @Test
    void rollsBackEverythingWhenLaterComponentFails() throws Exception {
        var skillReg = new RecordingSkillRegistry();
        var env = new PluginEnvironment();
        var registrar = new KairoComponentRegistrar(skillReg, null, env);

        Path root = Fixtures.copyToTemp("full-plugin");
        // First component succeeds; second fails because file does not exist.
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.SkillComponent(
                                "greet", root.resolve("skills/greet/SKILL.md"), "ns"),
                        new PluginComponent.SkillComponent(
                                "missing", root.resolve("does/not/exist.md"), "ns"));
        assertThatThrownBy(
                        () -> registrar.registerAll("p1", components).block(Duration.ofSeconds(2)))
                .hasCauseInstanceOf(java.io.IOException.class);

        // After rollback, the registered count should be 0 and the unregister callback fired.
        assertThat(registrar.registeredCount("p1")).isZero();
        assertThat(skillReg.unregistered).contains("greet"); // first skill was undone
    }

    @Test
    void unregisterAllReleasesEverything() throws Exception {
        var skillReg = new RecordingSkillRegistry();
        var env = new PluginEnvironment();
        var mcpStub = new StubMcpPlugin();
        var registrar = new KairoComponentRegistrar(skillReg, new PluginMcpRegistrar(mcpStub), env);

        Path root = Fixtures.copyToTemp("full-plugin");
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.SkillComponent(
                                "greet", root.resolve("skills/greet/SKILL.md"), "ns"),
                        new PluginComponent.BinComponent("h", root.resolve("bin/hello.sh")));
        registrar.registerAll("p1", components).block(Duration.ofSeconds(2));
        assertThat(env.activeBinDirs()).hasSize(1);

        registrar.unregisterAll("p1").block(Duration.ofSeconds(2));
        assertThat(env.activeBinDirs()).isEmpty();
        assertThat(skillReg.unregistered).contains("greet");
        assertThat(registrar.registeredCount("p1")).isZero();
    }

    @Test
    void unsupportedSkillUnregisterDoesNotBreakRollback() throws Exception {
        // A SkillRegistry that throws UOE on unregister (the default API behaviour).
        var skillReg = new RecordingSkillRegistry();
        skillReg.throwOnUnregister = true;
        var registrar = new KairoComponentRegistrar(skillReg, null, null);

        Path root = Fixtures.copyToTemp("full-plugin");
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.SkillComponent(
                                "greet", root.resolve("skills/greet/SKILL.md"), "ns"),
                        new PluginComponent.SkillComponent(
                                "missing", root.resolve("nope.md"), "ns"));
        assertThatThrownBy(
                        () -> registrar.registerAll("p1", components).block(Duration.ofSeconds(2)))
                .isNotNull();
        // No exception bubbles up despite UOE during rollback.
        assertThat(registrar.registeredCount("p1")).isZero();
    }

    @Test
    void unknownComponentTypesAreIgnoredButCounted() throws Exception {
        // Hook/OutputStyle/Theme currently have no binding — ensure they don't crash.
        var registrar = new KairoComponentRegistrar(null, null, null);
        Path root = Fixtures.copyToTemp("full-plugin");
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.OutputStyleComponent(
                                "concise", root.resolve("output-styles/concise.md")),
                        new PluginComponent.ThemeComponent("any", root.resolve("themes/any.md")));
        registrar.registerAll("p1", components).block(Duration.ofSeconds(2));
        assertThat(registrar.registeredCount("p1")).isEqualTo(2);
    }

    @Test
    void registersAgentToSubagentRegistryWhenWired() throws Exception {
        var subagentRegistry = new DefaultSubagentRegistry();
        var registrar = new KairoComponentRegistrar(null, null, null, subagentRegistry);

        Path root = Fixtures.copyToTemp("full-plugin");
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.AgentComponent(
                                "rev", root.resolve("agents/reviewer.md"), "ns"));
        registrar.registerAll("p1", components).block(Duration.ofSeconds(2));

        // Subagent should be registered using its qualified name.
        assertThat(subagentRegistry.get("ns:reviewer")).isPresent();
        assertThat(registrar.registeredCount("p1")).isEqualTo(1);
    }

    @Test
    void unregisterAgentRemovesFromSubagentRegistry() throws Exception {
        var subagentRegistry = new DefaultSubagentRegistry();
        var registrar = new KairoComponentRegistrar(null, null, null, subagentRegistry);

        Path root = Fixtures.copyToTemp("full-plugin");
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.AgentComponent(
                                "rev", root.resolve("agents/reviewer.md"), "ns"));
        registrar.registerAll("p1", components).block(Duration.ofSeconds(2));
        registrar.unregisterAll("p1").block(Duration.ofSeconds(2));
        assertThat(subagentRegistry.size()).isZero();
    }

    @Test
    void agentComponentsAreCapturedWhenSubagentRegistryIsNull() throws Exception {
        // 3-arg constructor → no subagent binding; should not throw.
        var registrar = new KairoComponentRegistrar(null, null, null);
        Path root = Fixtures.copyToTemp("full-plugin");
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.AgentComponent(
                                "rev", root.resolve("agents/reviewer.md"), "ns"));
        registrar.registerAll("p1", components).block(Duration.ofSeconds(2));
        assertThat(registrar.registeredCount("p1")).isEqualTo(1);
    }

    @Test
    void noOpRegistrarTracksCountsOnly() {
        var noop = ComponentRegistrar.noOp();
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.BinComponent("x", Path.of("/tmp/x")),
                        new PluginComponent.BinComponent("y", Path.of("/tmp/y")));
        noop.registerAll("p", components).block(Duration.ofSeconds(1));
        assertThat(noop.registeredCount("p")).isEqualTo(2);
        noop.unregisterAll("p").block(Duration.ofSeconds(1));
        assertThat(noop.registeredCount("p")).isZero();
    }

    @Test
    void registerAllOnEmptyListIsNoOp() {
        var registrar = new KairoComponentRegistrar(null, null, null);
        registrar.registerAll("p", List.of()).block(Duration.ofSeconds(1));
        registrar.registerAll("p", null).block(Duration.ofSeconds(1));
        assertThat(registrar.registeredCount("p")).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** SkillRegistry stub recording every interaction. */
    private static final class RecordingSkillRegistry implements SkillRegistry {
        final List<Path> loadedFiles = new ArrayList<>();
        final List<String> unregistered = new ArrayList<>();
        boolean throwOnUnregister = false;
        private final Map<String, SkillDefinition> stored = new HashMap<>();

        @Override
        public void register(SkillDefinition skill) {
            stored.put(skill.name(), skill);
        }

        @Override
        public Optional<SkillDefinition> get(String name) {
            return Optional.ofNullable(stored.get(name));
        }

        @Override
        public List<SkillDefinition> list() {
            return List.copyOf(stored.values());
        }

        @Override
        public List<SkillDefinition> listByCategory(SkillCategory category) {
            return List.of();
        }

        @Override
        public Mono<SkillDefinition> loadFromFile(Path path) {
            return Mono.fromCallable(
                    () -> {
                        if (!java.nio.file.Files.exists(path)) {
                            throw new java.io.IOException("not found: " + path);
                        }
                        loadedFiles.add(path);
                        // Use the file name (sans .md) as the skill name; matches what real loaders
                        // do when the frontmatter has no explicit name.
                        String n = path.getFileName().toString().replace(".md", "");
                        if (n.equals("SKILL")) {
                            // skill bundle uses parent dir name
                            n = path.getParent().getFileName().toString();
                        }
                        var def =
                                new SkillDefinition(
                                        n,
                                        "1.0.0",
                                        "test",
                                        "body",
                                        List.of(),
                                        SkillCategory.GENERAL,
                                        List.of(),
                                        List.of(),
                                        null,
                                        0,
                                        List.of(),
                                        null);
                        stored.put(n, def);
                        return def;
                    });
        }

        @Override
        public Mono<SkillDefinition> loadFromUrl(String url) {
            return Mono.empty();
        }

        @Override
        public Mono<SkillDefinition> loadFromClasspath(String resourcePath) {
            return Mono.empty();
        }

        @Override
        public void unregister(String name) {
            if (throwOnUnregister) throw new UnsupportedOperationException("not supported");
            unregistered.add(name);
            stored.remove(name);
        }
    }

    /** McpPlugin stub that always succeeds and tracks calls. */
    private static final class StubMcpPlugin implements McpPlugin {
        final AtomicInteger registeredCount = new AtomicInteger(0);

        @Override
        public boolean supports(Object cfg) {
            return true;
        }

        @Override
        public Mono<McpPluginRegistration> register(Object cfg) {
            registeredCount.incrementAndGet();
            return Mono.just(new McpPluginRegistration("stub", List.of()));
        }

        @Override
        public void close() {}
    }
}
