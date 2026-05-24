/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.variable;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginVariableResolverTest {

    @Test
    void resolvesKairoCanonicalNames() {
        var r = newResolver();
        assertThat(r.resolve("${KAIRO_PLUGIN_ROOT}/skills")).endsWith("/root/skills");
        assertThat(r.resolve("${KAIRO_PLUGIN_DATA}")).endsWith("/data");
        assertThat(r.resolve("${KAIRO_PROJECT_DIR}")).endsWith("/project");
    }

    @Test
    void resolvesClaudeAliases() {
        var r = newResolver();
        assertThat(r.resolve("${CLAUDE_PLUGIN_ROOT}/skills")).endsWith("/root/skills");
        assertThat(r.resolve("${CLAUDE_PLUGIN_DATA}")).endsWith("/data");
        assertThat(r.resolve("${CLAUDE_PROJECT_DIR}")).endsWith("/project");
    }

    @Test
    void claudeAndKairoExpandToSameValues() {
        var r = newResolver();
        assertThat(r.resolve("${CLAUDE_PLUGIN_ROOT}")).isEqualTo(r.resolve("${KAIRO_PLUGIN_ROOT}"));
    }

    @Test
    void leavesUnknownVariableAsIs() {
        var r = newResolver();
        assertThat(r.resolve("hello ${UNKNOWN_VAR} world")).isEqualTo("hello ${UNKNOWN_VAR} world");
    }

    @Test
    void resolvesMultipleOccurrences() {
        var r = newResolver();
        String resolved = r.resolve("--plugin=${KAIRO_PLUGIN_ROOT} --data=${KAIRO_PLUGIN_DATA}");
        assertThat(resolved).contains("/root").contains("/data");
    }

    @Test
    void returnsInputUnchangedWhenNoVariables() {
        var r = newResolver();
        assertThat(r.resolve("plain text")).isEqualTo("plain text");
        assertThat(r.resolve(null)).isNull();
    }

    @Test
    void customVariableOverridesDefaults() {
        var r = newResolver().with("CUSTOM", "/foo");
        assertThat(r.resolve("${CUSTOM}/bar")).isEqualTo("/foo/bar");
    }

    @Test
    void handlesNullPathsGracefully() {
        var r = new PluginVariableResolver(Path.of("/root"), null, null);
        assertThat(r.bindings()).containsKey("KAIRO_PLUGIN_ROOT");
        assertThat(r.bindings()).doesNotContainKey("KAIRO_PLUGIN_DATA");
    }

    private PluginVariableResolver newResolver() {
        return new PluginVariableResolver(Path.of("/root"), Path.of("/data"), Path.of("/project"));
    }
}
