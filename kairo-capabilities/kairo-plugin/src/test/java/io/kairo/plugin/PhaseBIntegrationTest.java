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

import io.kairo.api.mcp.McpPlugin;
import io.kairo.api.mcp.McpPluginRegistration;
import io.kairo.api.plugin.PluginComponent;
import io.kairo.api.plugin.PluginEvent;
import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginManifest;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
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
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

/**
 * End-to-end Phase B integration test: a plugin in the {@code .kairo-plugin/} directory layout
 * (Claude Code-compatible schema) is installed, enabled, and disabled — and every parsed component
 * is observed reaching its host registry.
 *
 * <p>This is the exercise the user asked for: "全搞完一起测试吧". It uses the {@code full-plugin} fixture
 * which contains every component type (skill, command, agent, hook, mcp, output-style, bin,
 * manifest mcpServers) in one tree.
 */
class PhaseBIntegrationTest {

    @Test
    void fullPluginFlowsThroughInstallEnableDisableUninstall(@TempDir Path tmp) throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");

        // 1. Build the wiring chain — exactly how a host application would.
        var pluginRegistry = new DefaultPluginRegistry();
        var loader = new PluginLoader();
        var skillRegistry = new RecordingSkillRegistry();
        var mcpStub = new RecordingMcpPlugin();
        var mcpRegistrar = new PluginMcpRegistrar(mcpStub);
        var environment = new PluginEnvironment();
        var registrar = new KairoComponentRegistrar(skillRegistry, mcpRegistrar, environment);
        var manager = new DefaultPluginManager(pluginRegistry, loader, tmp, registrar);

        var events = new ArrayList<PluginEvent>();
        var subscription = manager.events().subscribe(events::add);

        // 2. Verify the loader sees every component type before we install.
        PluginManifest preview = loader.load(root, null);
        assertThat(preview.components())
                .extracting(c -> c.getClass().getSimpleName())
                .contains(
                        "SkillComponent",
                        "CommandComponent",
                        "AgentComponent",
                        "HookComponent",
                        "McpComponent",
                        "OutputStyleComponent",
                        "BinComponent");

        // 3. install — adds to registry, disabled by default
        PluginInstallation installed =
                manager.install(new PluginSource.LocalPath(root), PluginScope.PROJECT)
                        .block(Duration.ofSeconds(5));
        assertThat(installed).isNotNull();
        assertThat(installed.metadata().name()).isEqualTo("full-fixture");
        assertThat(installed.enabled()).isFalse();
        assertThat(skillRegistry.loadedFiles).isEmpty(); // no binding before enable

        // 4. enable — components register atomically
        manager.enable(installed.id()).block(Duration.ofSeconds(10));

        assertThat(pluginRegistry.get(installed.id()))
                .isPresent()
                .map(PluginInstallation::enabled)
                .contains(true);

        // 4a. SkillComponent (skills/greet/SKILL.md) registered via SkillRegistry.loadFromFile
        assertThat(skillRegistry.loadedFiles).isNotEmpty();
        assertThat(skillRegistry.loadedFiles)
                .anyMatch(p -> p.getFileName().toString().equals("SKILL.md"));

        // 4b. CommandComponent (commands/quick-greet.md) also registered as flat skill
        assertThat(skillRegistry.loadedFiles)
                .anyMatch(p -> p.getFileName().toString().equals("quick-greet.md"));

        // 4c. McpComponents — secondary (.mcp.json) + demo (manifest mcpServers) — both registered
        assertThat(mcpStub.registered).contains("secondary", "demo");

        // 4d. BinComponent (bin/hello.sh) — its directory is in the augmented PATH
        assertThat(environment.activeBinDirs()).hasSize(1);
        assertThat(environment.augmentedPath("/usr/bin")).contains("/usr/bin").contains("/bin");

        // 4e. registrar sees every component
        assertThat(registrar.registeredCount(installed.id()))
                .isEqualTo(preview.components().size());

        // 5. disable — everything unwinds
        manager.disable(installed.id()).block(Duration.ofSeconds(5));
        assertThat(pluginRegistry.get(installed.id()))
                .isPresent()
                .map(PluginInstallation::enabled)
                .contains(false);
        assertThat(environment.activeBinDirs()).isEmpty();
        assertThat(mcpRegistrar.snapshot()).doesNotContainKey(installed.id());
        assertThat(registrar.registeredCount(installed.id())).isZero();

        // 6. uninstall — removes from registry
        manager.uninstall(installed.id()).block(Duration.ofSeconds(5));
        assertThat(pluginRegistry.get(installed.id())).isEmpty();

        // 7. Lifecycle events captured in order
        subscription.dispose();
        assertThat(events)
                .extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Installed", "Enabled", "Disabled", "Uninstalled");
    }

    @Test
    void enableFailureRollsBackEverything(@TempDir Path tmp) throws Exception {
        // Construct a synthetic plugin layout where the SkillComponent points at a missing file
        // so loadFromFile throws — this triggers the rollback path through KairoComponentRegistrar.
        Path root = tmp.resolve("broken-plugin");
        java.nio.file.Files.createDirectories(root.resolve(".kairo-plugin"));
        java.nio.file.Files.writeString(
                root.resolve(".kairo-plugin/plugin.json"),
                "{\"name\":\"broken\",\"version\":\"1.0.0\"}");
        // Create an existing skill file so the first registration succeeds; the SECOND skill
        // declared in our component list will reference a non-existent file and force a failure.
        java.nio.file.Files.createDirectories(root.resolve("skills/ok"));
        java.nio.file.Files.writeString(
                root.resolve("skills/ok/SKILL.md"), "---\nname: ok\n---\nbody");

        var pluginRegistry = new DefaultPluginRegistry();
        var loader = new PluginLoader();
        var skillRegistry = new RecordingSkillRegistry();
        var environment = new PluginEnvironment();
        var registrar = new KairoComponentRegistrar(skillRegistry, null, environment);
        var manager = new DefaultPluginManager(pluginRegistry, loader, tmp, registrar);

        // Install normally — only the "ok" skill is real.
        PluginInstallation installed =
                manager.install(new PluginSource.LocalPath(root), PluginScope.USER)
                        .block(Duration.ofSeconds(5));
        assertThat(installed).isNotNull();

        // Inject a broken component manually by extending the loaded manifest after the fact —
        // since the loader won't produce one. We achieve this by enabling, then attempting to
        // re-register through the registrar with a synthetic broken component list.
        manager.enable(installed.id()).block(Duration.ofSeconds(5));
        assertThat(skillRegistry.loadedFiles).hasSize(1);

        // Now drive a fresh failing flow directly on the registrar to validate rollback semantics.
        var components =
                List.<PluginComponent>of(
                        new PluginComponent.SkillComponent(
                                "ok", root.resolve("skills/ok/SKILL.md"), "broken"),
                        new PluginComponent.SkillComponent(
                                "missing", root.resolve("skills/does-not-exist.md"), "broken"));
        try {
            registrar.registerAll("rollback-test", components).block(Duration.ofSeconds(5));
            org.assertj.core.api.Assertions.fail("expected failure");
        } catch (Exception expected) {
            // ok — verify rollback happened
            assertThat(registrar.registeredCount("rollback-test")).isZero();
            assertThat(skillRegistry.unregistered).contains("ok");
        }
    }

    @Test
    void disabledHooksFlagPreventsHookRegistration(@TempDir Path tmp) throws Exception {
        Path root = Fixtures.copyToTemp("disabled-hooks-plugin");
        var pluginRegistry = new DefaultPluginRegistry();
        var loader = new PluginLoader();
        var registrar = new KairoComponentRegistrar(null, null, null);
        var manager = new DefaultPluginManager(pluginRegistry, loader, tmp, registrar);

        PluginInstallation installed =
                manager.install(new PluginSource.LocalPath(root), PluginScope.USER)
                        .block(Duration.ofSeconds(5));
        manager.enable(installed.id()).block(Duration.ofSeconds(5));

        // The plugin's hooks/hooks.json declares disableAllHooks=true so HookComponentLoader
        // returns an empty list — verify the manifest reflects that.
        PluginManifest m = loader.load(root, null);
        assertThat(m.components())
                .filteredOn(c -> c instanceof PluginComponent.HookComponent)
                .isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static final class RecordingSkillRegistry implements SkillRegistry {
        final List<Path> loadedFiles = new ArrayList<>();
        final List<String> unregistered = new ArrayList<>();
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
        public List<SkillDefinition> listByCategory(SkillCategory c) {
            return List.of();
        }

        @Override
        public Mono<SkillDefinition> loadFromFile(Path path) {
            return Mono.fromCallable(
                    () -> {
                        if (!java.nio.file.Files.exists(path))
                            throw new java.io.IOException("not found: " + path);
                        loadedFiles.add(path);
                        String n = path.getFileName().toString().replace(".md", "");
                        if (n.equals("SKILL")) n = path.getParent().getFileName().toString();
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
        public Mono<SkillDefinition> loadFromUrl(String u) {
            return Mono.empty();
        }

        @Override
        public Mono<SkillDefinition> loadFromClasspath(String r) {
            return Mono.empty();
        }

        @Override
        public void unregister(String name) {
            unregistered.add(name);
            stored.remove(name);
        }
    }

    private static final class RecordingMcpPlugin implements McpPlugin {
        final List<String> registered = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger(0);

        @Override
        public boolean supports(Object cfg) {
            return true;
        }

        @Override
        public Mono<McpPluginRegistration> register(Object cfg) {
            calls.incrementAndGet();
            String name = "" + cfg.toString();
            // McpServerConfig record's toString starts with "McpServerConfig[name=...]" — pull it
            // out for sanity.
            try {
                var nameMethod = cfg.getClass().getMethod("name");
                Object n = nameMethod.invoke(cfg);
                registered.add(String.valueOf(n));
                return Mono.just(new McpPluginRegistration(String.valueOf(n), List.of()));
            } catch (Exception e) {
                registered.add(name);
                return Mono.just(new McpPluginRegistration(name, List.of()));
            }
        }

        @Override
        public void close() {}
    }
}
