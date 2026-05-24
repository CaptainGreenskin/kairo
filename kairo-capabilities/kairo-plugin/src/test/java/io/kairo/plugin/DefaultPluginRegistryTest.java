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

import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginMetadata;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultPluginRegistryTest {

    @Test
    void putAndGetRoundTrip() {
        var reg = new DefaultPluginRegistry();
        var inst = installation("a", PluginScope.USER, false);
        reg.put(inst);
        assertThat(reg.get("a")).contains(inst);
        assertThat(reg.size()).isEqualTo(1);
    }

    @Test
    void putReplacesExistingId() {
        var reg = new DefaultPluginRegistry();
        reg.put(installation("a", PluginScope.USER, false));
        reg.put(installation("a", PluginScope.PROJECT, true));
        assertThat(reg.get("a")).map(PluginInstallation::scope).contains(PluginScope.PROJECT);
        assertThat(reg.size()).isEqualTo(1);
    }

    @Test
    void removeReturnsTrueWhenPresent() {
        var reg = new DefaultPluginRegistry();
        reg.put(installation("a", PluginScope.USER, false));
        assertThat(reg.remove("a")).isTrue();
        assertThat(reg.remove("a")).isFalse();
    }

    @Test
    void listByScopeFilters() {
        var reg = new DefaultPluginRegistry();
        reg.put(installation("a", PluginScope.USER, false));
        reg.put(installation("b", PluginScope.PROJECT, true));
        reg.put(installation("c", PluginScope.PROJECT, false));
        assertThat(reg.listByScope(PluginScope.PROJECT)).hasSize(2);
        assertThat(reg.listByScope(PluginScope.LOCAL)).isEmpty();
    }

    @Test
    void listEnabledFilters() {
        var reg = new DefaultPluginRegistry();
        reg.put(installation("a", PluginScope.USER, false));
        reg.put(installation("b", PluginScope.USER, true));
        assertThat(reg.listEnabled()).extracting(PluginInstallation::id).containsExactly("b");
    }

    @Test
    void listIsImmutableSnapshot() {
        var reg = new DefaultPluginRegistry();
        reg.put(installation("a", PluginScope.USER, false));
        List<PluginInstallation> snapshot = reg.list();
        reg.put(installation("b", PluginScope.USER, false));
        assertThat(snapshot).hasSize(1); // snapshot is unaffected
    }

    @Test
    void clearRemovesAll() {
        var reg = new DefaultPluginRegistry();
        reg.put(installation("a", PluginScope.USER, false));
        reg.put(installation("b", PluginScope.USER, false));
        reg.clear();
        assertThat(reg.size()).isZero();
    }

    private static PluginInstallation installation(String id, PluginScope scope, boolean enabled) {
        return new PluginInstallation(
                id,
                new PluginMetadata(id, "1.0.0", null, null, null, null, List.of(), List.of()),
                new PluginSource.LocalPath(Path.of("/tmp/" + id)),
                scope,
                enabled,
                Path.of("/tmp/" + id),
                Path.of("/tmp/data/" + id),
                Instant.now());
    }
}
