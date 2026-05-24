/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.agent.SubagentDefinition;
import io.kairo.api.plugin.MarketplaceCatalog;
import io.kairo.api.plugin.MarketplaceEntry;
import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginMetadata;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Sanity tests for the @Experimental plugin SPI records: validation, equality, helpers. */
class PluginRecordsTest {

    @Test
    void pluginMetadataRejectsBlankName() {
        assertThatThrownBy(
                        () ->
                                new PluginMetadata(
                                        "  ", "1.0.0", null, null, null, null, List.of(),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pluginMetadataRejectsBlankVersion() {
        assertThatThrownBy(
                        () ->
                                new PluginMetadata(
                                        "x", null, null, null, null, null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pluginMetadataDefensivelyCopiesLists() {
        var keywords = new java.util.ArrayList<>(List.of("a"));
        var deps = new java.util.ArrayList<PluginMetadata.Dependency>();
        var m = new PluginMetadata("x", "1.0.0", null, null, null, null, keywords, deps);
        keywords.add("mutated");
        deps.add(new PluginMetadata.Dependency("y", "1.0.0"));
        assertThat(m.keywords()).containsExactly("a");
        assertThat(m.dependencies()).isEmpty();
    }

    @Test
    void pluginInstallationWithEnabledFlipsFlag() {
        var meta = new PluginMetadata("x", "1.0.0", null, null, null, null, List.of(), List.of());
        var i =
                new PluginInstallation(
                        "id",
                        meta,
                        new PluginSource.LocalPath(Path.of("/tmp")),
                        PluginScope.USER,
                        false,
                        Path.of("/tmp"),
                        Path.of("/tmp/data"),
                        Instant.now());
        assertThat(i.withEnabled(true).enabled()).isTrue();
        assertThat(i.enabled()).isFalse(); // original unchanged
    }

    @Test
    void marketplaceEntryRequiresName() {
        assertThatThrownBy(
                        () ->
                                new MarketplaceEntry(
                                        "  ",
                                        new PluginSource.LocalPath(Path.of("/x")),
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marketplaceCatalogDefaultsToUnknownTrust() {
        var c = new MarketplaceCatalog("m", "alice", null, List.of(), null);
        assertThat(c.trustLevel()).isEqualTo("unknown");
    }

    @Test
    void pluginSourceTypeNamesAreStable() {
        assertThat(new PluginSource.LocalPath(Path.of("/")).type()).isEqualTo("path");
        assertThat(new PluginSource.GitHub("a/b", null, null).type()).isEqualTo("github");
        assertThat(new PluginSource.GitUrl("git://x", null).type()).isEqualTo("git");
        assertThat(new PluginSource.GitSubdir("git://x", null, "sub").type())
                .isEqualTo("git-subdir");
        assertThat(new PluginSource.Npm("pkg", "1.0.0", null).type()).isEqualTo("npm");
    }

    @Test
    void subagentQualifiedNameComposesNamespace() {
        var s = new SubagentDefinition("rev", "desc", "prompt body", List.of(), null, "myplugin");
        assertThat(s.qualifiedName()).isEqualTo("myplugin:rev");

        var bare = new SubagentDefinition("rev", "desc", "prompt body", List.of(), null, null);
        assertThat(bare.qualifiedName()).isEqualTo("rev");
    }
}
