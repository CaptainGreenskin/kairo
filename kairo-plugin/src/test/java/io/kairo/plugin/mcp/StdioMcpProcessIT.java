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
import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import io.kairo.mcp.McpServerConfig;
import io.kairo.plugin.ComponentRegistrar;
import io.kairo.plugin.DefaultPluginManager;
import io.kairo.plugin.DefaultPluginRegistry;
import io.kairo.plugin.KairoComponentRegistrar;
import io.kairo.plugin.PluginEnvironment;
import io.kairo.plugin.PluginLoader;
import io.kairo.plugin.testsupport.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

/**
 * Real subprocess-launching integration test for the {@code .mcp.json} → stdio pipeline. Unlike
 * {@link PluginMcpRegistrarTest}, which uses a stub {@link McpPlugin}, this IT wires a real {@link
 * McpPlugin} that calls {@link ProcessBuilder#start()} and verifies the spawned process really:
 *
 * <ol>
 *   <li>Receives the resolved {@code ${KAIRO_PLUGIN_ROOT}} path (variable substitution honoured).
 *   <li>Sees inline-declared environment variables, including ones containing {@code
 *       ${KAIRO_PLUGIN_ROOT}} (env-value substitution honoured).
 *   <li>Receives the args verbatim (also after substitution).
 *   <li>Stays alive after {@code enable()} returns.
 *   <li>Is reachable from the registrar's per-plugin handle so disable can clean up.
 * </ol>
 *
 * <p>POSIX-only: the fixture ships a bash script under {@code scripts/}, and Windows path quoting +
 * fork semantics are different enough that wiring up a parallel CMD/PowerShell fixture isn't worth
 * it for a single test.
 */
@Tag("integration")
@DisabledOnOs(OS.WINDOWS)
class StdioMcpProcessIT {

    private final List<Process> spawned = new CopyOnWriteArrayList<>();

    @AfterEach
    void killEverythingWeStarted() {
        for (Process p : spawned) {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    @Test
    @DisplayName(
            "install + enable plugin → real subprocess spawned with resolved KAIRO_PLUGIN_ROOT,"
                    + " args, and env")
    void enablingPluginActuallySpawnsStdioSubprocess(@TempDir Path tmp) throws Exception {
        Path pluginRoot = Fixtures.copyToTemp("stdio-mcp-plugin");
        // copyTree() doesn't preserve POSIX perms — restore the executable bit on our shell script.
        makeExecutable(pluginRoot.resolve("scripts/echo-mcp.sh"));

        // Path the shell script writes its argv + env into. Lives under tmp so JUnit cleans it.
        Path probeFile = tmp.resolve("probe-" + UUID.randomUUID() + ".txt");

        var capturedConfigs = new ArrayList<McpServerConfig>();
        var realLauncher = new RealStdioMcpPlugin(spawned, probeFile, capturedConfigs);
        var mcpRegistrar = new PluginMcpRegistrar(realLauncher);
        var environment = new PluginEnvironment();
        ComponentRegistrar registrar =
                new KairoComponentRegistrar(null, mcpRegistrar, environment, null);

        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(),
                        new PluginLoader(),
                        tmp.resolve("data"),
                        registrar);

        PluginInstallation inst =
                manager.install(new PluginSource.LocalPath(pluginRoot), PluginScope.USER)
                        .block(Duration.ofSeconds(10));
        assertThat(inst).isNotNull();
        manager.enable(inst.id()).block(Duration.ofSeconds(10));

        // Registrar should have seen one server with our substituted command + env.
        assertThat(capturedConfigs).hasSize(1);
        McpServerConfig cfg = capturedConfigs.get(0);
        assertThat(cfg.name()).isEqualTo("echo-mcp");
        assertThat(cfg.command().get(0))
                .as("KAIRO_PLUGIN_ROOT must be resolved to an absolute path before exec")
                .doesNotContain("${")
                .endsWith("/scripts/echo-mcp.sh");
        assertThat(cfg.command()).hasSize(2);
        assertThat(cfg.command().get(1))
                .as("KAIRO_PLUGIN_ROOT inside an arg is also substituted")
                .startsWith("--plugin-root=")
                .doesNotContain("${");
        assertThat(cfg.env())
                .as("env values containing ${KAIRO_PLUGIN_ROOT} are substituted")
                .containsEntry("KAIRO_TEST_VAR", "fixture-value")
                .containsKey("PLUGIN_ROOT_ECHO");
        assertThat(cfg.env().get("PLUGIN_ROOT_ECHO")).doesNotContain("${");

        // The subprocess must actually start.
        assertThat(spawned).hasSize(1);
        Process child = spawned.get(0);
        assertThat(child.isAlive())
                .as("subprocess should still be running after enable()")
                .isTrue();

        // The shell script writes its captured env to probeFile before blocking on cat.
        // Wait briefly for that file (≤ 5s).
        long deadline = System.currentTimeMillis() + 5_000;
        while (!Files.isRegularFile(probeFile) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(Files.isRegularFile(probeFile))
                .as("subprocess should have written its probe file")
                .isTrue();
        String probe = Files.readString(probeFile);
        assertThat(probe).contains("KAIRO_TEST_VAR=fixture-value");
        assertThat(probe).contains("PLUGIN_ROOT_ECHO=" + pluginRoot.toAbsolutePath());
        assertThat(probe).contains("argv:--plugin-root=" + pluginRoot.toAbsolutePath());

        // Registrar tracks the server for the plugin id.
        assertThat(mcpRegistrar.snapshot()).containsKey(inst.id());
        assertThat(mcpRegistrar.snapshot().get(inst.id()))
                .extracting(PluginMcpRegistrar.ServerHandle::serverName)
                .containsExactly("echo-mcp");

        // Disable the plugin → kairo-mcp would normally tear down via its own bookkeeping;
        // the registrar drops the per-plugin handle. We then verify our captured Process
        // can be terminated (proves we have a handle on the real OS process).
        manager.disable(inst.id()).block(Duration.ofSeconds(5));
        assertThat(mcpRegistrar.snapshot()).doesNotContainKey(inst.id());

        child.destroy();
        boolean exited = child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(exited).as("subprocess should exit after destroy()").isTrue();
    }

    private static void makeExecutable(Path script) throws IOException {
        Set<PosixFilePermission> perms =
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(script, PosixFilePermissions.asFileAttribute(perms).value());
    }

    /**
     * Minimal real {@link McpPlugin} — instead of speaking JSON-RPC, it just executes the config's
     * command via {@link ProcessBuilder} so we can prove the wiring really forks a process.
     * Captures configs and Processes for assertions.
     */
    private static final class RealStdioMcpPlugin implements McpPlugin {
        private final List<Process> spawned;
        private final Path probeFile;
        private final List<McpServerConfig> captured;

        RealStdioMcpPlugin(List<Process> spawned, Path probeFile, List<McpServerConfig> captured) {
            this.spawned = spawned;
            this.probeFile = probeFile;
            this.captured = captured;
        }

        @Override
        public boolean supports(Object cfg) {
            return cfg instanceof McpServerConfig;
        }

        @Override
        public Mono<McpPluginRegistration> register(Object cfg) {
            return Mono.fromCallable(
                    () -> {
                        McpServerConfig c = (McpServerConfig) cfg;
                        captured.add(c);
                        ProcessBuilder pb = new ProcessBuilder(c.command());
                        pb.environment().put("KAIRO_TEST_PROBE_FILE", probeFile.toString());
                        for (Map.Entry<String, String> e : c.env().entrySet()) {
                            pb.environment().put(e.getKey(), e.getValue());
                        }
                        // redirect stderr so test logs surface bash errors.
                        pb.redirectErrorStream(true);
                        // Make sure relative `bin/` paths still resolve if any — anchor at script
                        // dir.
                        Path scriptDir = Paths.get(c.command().get(0)).getParent();
                        if (scriptDir != null) {
                            pb.directory(scriptDir.toFile());
                        }
                        Process p = pb.start();
                        spawned.add(p);
                        return new McpPluginRegistration(c.name(), List.of());
                    });
        }

        @Override
        public void close() {
            for (Process p : spawned) {
                if (p.isAlive()) p.destroyForcibly();
            }
        }
    }
}
