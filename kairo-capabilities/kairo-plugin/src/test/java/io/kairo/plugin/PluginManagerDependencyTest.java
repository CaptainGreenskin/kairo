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

import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import io.kairo.plugin.installer.DependencyResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test for Phase F.3: dependency resolution wired into {@code DefaultPluginManager}.
 * Sets up real on-disk plugins with cross-dependencies and verifies enable/disable propagation
 * through the manager's public API.
 */
class PluginManagerDependencyTest {

    @Test
    void enableAutomaticallyEnablesTransitiveDependencies(@TempDir Path tmp) throws Exception {
        Path alphaDir = writePlugin(tmp.resolve("alpha"), "alpha", "1.0.0", "");
        Path betaDir = writePlugin(tmp.resolve("beta"), "beta", "1.0.0", "alpha");
        Path gammaDir = writePlugin(tmp.resolve("gamma"), "gamma", "1.0.0", "beta");

        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new PluginLoader(), tmp.resolve("data"));
        var alpha =
                manager.install(new PluginSource.LocalPath(alphaDir), PluginScope.USER)
                        .block(Duration.ofSeconds(5));
        var beta =
                manager.install(new PluginSource.LocalPath(betaDir), PluginScope.USER)
                        .block(Duration.ofSeconds(5));
        var gamma =
                manager.install(new PluginSource.LocalPath(gammaDir), PluginScope.USER)
                        .block(Duration.ofSeconds(5));

        // Enabling only gamma should auto-enable beta then alpha first.
        manager.enable(gamma.id()).block(Duration.ofSeconds(5));

        assertThat(manager.list())
                .filteredOn(PluginInstallation::enabled)
                .extracting(p -> p.metadata().name())
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    @Test
    void disableCascadesToDependents(@TempDir Path tmp) throws Exception {
        Path alphaDir = writePlugin(tmp.resolve("alpha"), "alpha", "1.0.0", "");
        Path betaDir = writePlugin(tmp.resolve("beta"), "beta", "1.0.0", "alpha");
        Path gammaDir = writePlugin(tmp.resolve("gamma"), "gamma", "1.0.0", "beta");

        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new PluginLoader(), tmp.resolve("data"));
        var alpha =
                manager.install(new PluginSource.LocalPath(alphaDir), PluginScope.USER)
                        .block(Duration.ofSeconds(5));
        var beta =
                manager.install(new PluginSource.LocalPath(betaDir), PluginScope.USER)
                        .block(Duration.ofSeconds(5));
        var gamma =
                manager.install(new PluginSource.LocalPath(gammaDir), PluginScope.USER)
                        .block(Duration.ofSeconds(5));

        manager.enable(gamma.id()).block(Duration.ofSeconds(5));
        // Now disable alpha — should cascade and disable beta + gamma too.
        manager.disable(alpha.id()).block(Duration.ofSeconds(5));

        assertThat(manager.list())
                .filteredOn(PluginInstallation::enabled)
                .as("after cascade-disable of alpha, no plugins remain enabled")
                .isEmpty();
    }

    @Test
    void enableFailsLoudlyOnMissingDependency(@TempDir Path tmp) throws Exception {
        // beta declares dependency on alpha but alpha is never installed.
        Path betaDir = writePlugin(tmp.resolve("beta"), "beta", "1.0.0", "alpha");
        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new PluginLoader(), tmp.resolve("data"));
        var beta =
                manager.install(new PluginSource.LocalPath(betaDir), PluginScope.USER)
                        .block(Duration.ofSeconds(5));

        assertThatThrownBy(() -> manager.enable(beta.id()).block(Duration.ofSeconds(5)))
                .isInstanceOf(DependencyResolver.UnresolvableDependencyException.class)
                .hasMessageContaining("'alpha'");
    }

    @Test
    void enablingAlreadyEnabledPluginIsIdempotent(@TempDir Path tmp) throws Exception {
        Path dir = writePlugin(tmp.resolve("p"), "p", "1.0.0", "");
        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new PluginLoader(), tmp.resolve("data"));
        var p =
                manager.install(new PluginSource.LocalPath(dir), PluginScope.USER)
                        .block(Duration.ofSeconds(5));
        manager.enable(p.id()).block(Duration.ofSeconds(5));
        manager.enable(p.id()).block(Duration.ofSeconds(5));
        assertThat(manager.list().get(0).enabled()).isTrue();
    }

    /**
     * Writes a tiny plugin directory at {@code dir}: {@code .kairo-plugin/plugin.json} with the
     * given name/version, and an optional dependency on {@code dependsOn} (empty string = none).
     */
    private static Path writePlugin(Path dir, String name, String version, String dependsOn)
            throws Exception {
        Files.createDirectories(dir.resolve(".kairo-plugin"));
        String depsJson =
                dependsOn.isEmpty() ? "[]" : "[{\"name\":\"" + dependsOn + "\",\"version\":\"*\"}]";
        Files.writeString(
                dir.resolve(".kairo-plugin/plugin.json"),
                "{\n"
                        + "  \"name\": \""
                        + name
                        + "\",\n"
                        + "  \"version\": \""
                        + version
                        + "\",\n"
                        + "  \"dependencies\": "
                        + depsJson
                        + "\n"
                        + "}\n");
        return dir;
    }
}
