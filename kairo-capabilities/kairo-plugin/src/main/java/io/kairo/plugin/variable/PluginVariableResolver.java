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

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves plugin path variables inside config strings.
 *
 * <p>Canonical names are {@code ${KAIRO_*}}. {@code ${CLAUDE_*}} is supported as a backwards-compat
 * alias for ingesting Claude Code plugin packages unchanged. Both expand to the same values.
 *
 * <p>Recognised variables:
 *
 * <ul>
 *   <li>{@code ${KAIRO_PLUGIN_ROOT}} / {@code ${CLAUDE_PLUGIN_ROOT}} — plugin install dir
 *   <li>{@code ${KAIRO_PLUGIN_DATA}} / {@code ${CLAUDE_PLUGIN_DATA}} — persistent data dir
 *   <li>{@code ${KAIRO_PROJECT_DIR}} / {@code ${CLAUDE_PROJECT_DIR}} — current project root
 * </ul>
 */
public final class PluginVariableResolver {

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)\\}");

    private final Map<String, String> variables;

    public PluginVariableResolver(Path pluginRoot, Path pluginData, Path projectDir) {
        this.variables = new LinkedHashMap<>();
        bind("KAIRO_PLUGIN_ROOT", pluginRoot);
        bind("CLAUDE_PLUGIN_ROOT", pluginRoot);
        bind("KAIRO_PLUGIN_DATA", pluginData);
        bind("CLAUDE_PLUGIN_DATA", pluginData);
        bind("KAIRO_PROJECT_DIR", projectDir);
        bind("CLAUDE_PROJECT_DIR", projectDir);
    }

    private void bind(String name, Path value) {
        if (value != null) {
            variables.put(name, value.toAbsolutePath().toString());
        }
    }

    /** Adds a custom variable; overrides any existing binding. */
    public PluginVariableResolver with(String name, String value) {
        variables.put(name, value);
        return this;
    }

    /**
     * Replaces all {@code ${VAR}} occurrences in {@code input}. Unknown variables are left as-is.
     */
    public String resolve(String input) {
        if (input == null || !input.contains("${")) {
            return input;
        }
        Matcher m = VARIABLE.matcher(input);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String replacement = variables.get(name);
            m.appendReplacement(
                    out, Matcher.quoteReplacement(replacement != null ? replacement : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Snapshot view (mostly for tests). */
    public Map<String, String> bindings() {
        return Map.copyOf(variables);
    }
}
