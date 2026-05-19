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

import io.kairo.api.plugin.PluginComponent;
import io.kairo.api.plugin.PluginManifest;
import io.kairo.plugin.testsupport.Fixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginLoaderTest {

    private final PluginLoader loader = new PluginLoader();

    @Test
    void loadsMinimalPlugin() throws Exception {
        Path root = Fixtures.copyToTemp("minimal-plugin");
        PluginManifest m = loader.load(root, null);
        assertThat(m.metadata().name()).isEqualTo("minimal");
        assertThat(m.components()).isEmpty();
    }

    @Test
    void loadsFullPluginWithAllComponents() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        PluginManifest m = loader.load(root, null);

        List<PluginComponent> comps = m.components();
        assertThat(comps)
                .extracting(c -> c.getClass().getSimpleName())
                .contains(
                        "SkillComponent",
                        "CommandComponent",
                        "AgentComponent",
                        "HookComponent",
                        "McpComponent",
                        "OutputStyleComponent",
                        "BinComponent");
    }

    @Test
    void componentsAreSortedByRegistrationOrder() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        PluginManifest m = loader.load(root, null);
        List<Integer> orders = m.components().stream().map(PluginComponent::order).toList();
        // strictly non-decreasing
        for (int i = 1; i < orders.size(); i++) {
            assertThat(orders.get(i)).isGreaterThanOrEqualTo(orders.get(i - 1));
        }
    }

    @Test
    void mcpServersExtractedFromManifest() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        PluginManifest m = loader.load(root, null);
        assertThat(m.mcpServers()).containsKey("demo");
    }

    @Test
    void namespaceDefaultsToPluginName() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        PluginManifest m = loader.load(root, null);
        var skill =
                (PluginComponent.SkillComponent)
                        m.components().stream()
                                .filter(c -> c instanceof PluginComponent.SkillComponent)
                                .findFirst()
                                .orElseThrow();
        assertThat(skill.namespace()).isEqualTo("full-fixture");
    }

    @Test
    void namespaceOverrideHonoured() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        PluginManifest m = loader.load(root, "custom-ns");
        var skill =
                (PluginComponent.SkillComponent)
                        m.components().stream()
                                .filter(c -> c instanceof PluginComponent.SkillComponent)
                                .findFirst()
                                .orElseThrow();
        assertThat(skill.namespace()).isEqualTo("custom-ns");
    }

    @Test
    void rejectsNonDirectoryRoot(@TempDir Path tmp) {
        assertThatThrownBy(() -> loader.load(tmp.resolve("does-not-exist"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void rejectsRangeVersionFromBadFixture() throws Exception {
        Path root = Fixtures.copyToTemp("bad-version-plugin");
        assertThatThrownBy(() -> loader.load(root, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-exact version");
    }
}
