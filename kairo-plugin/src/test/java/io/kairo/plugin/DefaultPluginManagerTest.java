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

import io.kairo.api.plugin.PluginEvent;
import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import io.kairo.plugin.testsupport.Fixtures;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultPluginManagerTest {

    @Test
    void installEnableDisableUninstallEmitsLifecycleEvents(@TempDir Path tmp) throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new ClaudePluginLoader(), tmp);

        List<PluginEvent> events = new ArrayList<>();
        var subscription = manager.events().subscribe(events::add);

        PluginInstallation installed =
                manager.install(new PluginSource.LocalPath(root), PluginScope.PROJECT)
                        .block(Duration.ofSeconds(5));
        assertThat(installed).isNotNull();
        assertThat(installed.metadata().name()).isEqualTo("full-fixture");
        assertThat(installed.scope()).isEqualTo(PluginScope.PROJECT);
        assertThat(installed.enabled()).isFalse();

        manager.enable(installed.id()).block(Duration.ofSeconds(5));
        assertThat(manager.list()).extracting(PluginInstallation::enabled).containsExactly(true);

        manager.disable(installed.id()).block(Duration.ofSeconds(5));
        assertThat(manager.list()).extracting(PluginInstallation::enabled).containsExactly(false);

        manager.uninstall(installed.id()).block(Duration.ofSeconds(5));
        assertThat(manager.list()).isEmpty();

        subscription.dispose();
        assertThat(events)
                .extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Installed", "Enabled", "Disabled", "Uninstalled");
    }

    @Test
    void rejectsRemoteSourcesInPhaseA(@TempDir Path tmp) {
        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new ClaudePluginLoader(), tmp);
        assertThatThrownBy(
                        () ->
                                manager.install(
                                                new PluginSource.GitHub("foo/bar", "main", null),
                                                PluginScope.USER)
                                        .block(Duration.ofSeconds(5)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void enableUnknownIdFails(@TempDir Path tmp) {
        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new ClaudePluginLoader(), tmp);
        assertThatThrownBy(() -> manager.enable("local:nope:xxx").block(Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doubleEnableIsIdempotent(@TempDir Path tmp) throws Exception {
        Path root = Fixtures.copyToTemp("minimal-plugin");
        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new ClaudePluginLoader(), tmp);
        PluginInstallation i =
                manager.install(new PluginSource.LocalPath(root), PluginScope.USER)
                        .block(Duration.ofSeconds(5));

        List<PluginEvent> events = new ArrayList<>();
        var subscription = manager.events().subscribe(events::add);
        manager.enable(i.id()).block(Duration.ofSeconds(5));
        manager.enable(i.id()).block(Duration.ofSeconds(5));
        subscription.dispose();
        // Second enable should not emit another Enabled event
        assertThat(events).extracting(e -> e.getClass().getSimpleName()).containsExactly("Enabled");
    }

    @Test
    void updateRereadsManifest(@TempDir Path tmp) throws Exception {
        Path root = Fixtures.copyToTemp("minimal-plugin");
        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new ClaudePluginLoader(), tmp);
        PluginInstallation i =
                manager.install(new PluginSource.LocalPath(root), PluginScope.USER)
                        .block(Duration.ofSeconds(5));

        // Bump on-disk version then update — should re-parse and emit Updated.
        java.nio.file.Files.writeString(
                root.resolve(".claude-plugin/plugin.json"),
                "{\"name\":\"minimal\",\"version\":\"1.1.0\"}");

        List<PluginEvent> seen = new ArrayList<>();
        var subscription = manager.events().subscribe(seen::add);
        manager.update(i.id()).block(Duration.ofSeconds(5));
        subscription.dispose();

        assertThat(seen).extracting(e -> e.getClass().getSimpleName()).containsExactly("Updated");
        assertThat(manager.list().get(0).metadata().version()).isEqualTo("1.1.0");
    }
}
