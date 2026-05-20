/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.plugin;

import io.kairo.api.Experimental;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * One concrete contribution from a plugin to a Kairo registry.
 *
 * <p>A single plugin can contribute many components of multiple types. The {@code PluginLoader}
 * translates the plugin's on-disk files into instances of these records and the {@code
 * PluginManager} routes each to the matching Kairo registry in this fixed order:
 *
 * <pre>tools → skills → agents → hooks → mcp → bin → outputStyles → themes</pre>
 *
 * <p>Component registration is atomic — if any step fails the previously-registered components are
 * rolled back.
 *
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public sealed interface PluginComponent {

    /** Sort key for the registration ordering above. Lower = earlier. */
    int order();

    /** Tool contribution — only Java native plugins (deferred to v1.3). */
    record ToolComponent(String name, Object toolHandle) implements PluginComponent {
        @Override
        public int order() {
            return 0;
        }
    }

    /** Skill contributed via {@code skills/<name>/SKILL.md} or root-level {@code SKILL.md}. */
    record SkillComponent(String name, Path skillFile, String namespace)
            implements PluginComponent {
        @Override
        public int order() {
            return 10;
        }
    }

    /** Flat skill (slash command) contributed via {@code commands/*.md}. */
    record CommandComponent(String name, Path commandFile, String namespace)
            implements PluginComponent {
        @Override
        public int order() {
            return 11;
        }
    }

    /** Subagent definition contributed via {@code agents/*.md}. */
    record AgentComponent(String name, Path agentFile, String namespace)
            implements PluginComponent {
        @Override
        public int order() {
            return 20;
        }
    }

    /** Hook binding contributed via {@code hooks/hooks.json}. */
    record HookComponent(String event, String matcher, List<HookAction> actions)
            implements PluginComponent {
        public HookComponent {
            if (event == null || event.isBlank()) {
                throw new IllegalArgumentException("Hook event must not be blank");
            }
            actions = actions == null ? List.of() : List.copyOf(actions);
        }

        @Override
        public int order() {
            return 30;
        }

        /**
         * One executable action attached to a hook event. The discriminator is {@link #type()} —
         * one of {@code command}, {@code http}, {@code mcp_tool}, {@code prompt}, {@code agent} —
         * and {@link #config()} carries the type-specific fields verbatim from {@code hooks.json}
         * (e.g. {@code command}/{@code args}/{@code shell}/{@code timeout} for {@code command};
         * {@code url}/{@code headers} for {@code http}; {@code server}/{@code tool}/{@code input}
         * for {@code mcp_tool}; etc.).
         */
        public record HookAction(String type, Map<String, Object> config) {
            public HookAction {
                if (type == null || type.isBlank()) {
                    throw new IllegalArgumentException("Hook action type must not be blank");
                }
                config = config == null ? Map.of() : Map.copyOf(config);
            }
        }
    }

    /**
     * MCP server contributed via {@code .mcp.json} or inline in {@code plugin.json#mcpServers}.
     * Started as a stdio subprocess when the plugin is enabled.
     */
    record McpComponent(
            String serverName, String command, List<String> args, Map<String, String> env)
            implements PluginComponent {
        public McpComponent {
            args = args == null ? List.of() : List.copyOf(args);
            env = env == null ? Map.of() : Map.copyOf(env);
        }

        @Override
        public int order() {
            return 40;
        }
    }

    /** Executable contributed via {@code bin/}; injected into agent shell-tool PATH. */
    record BinComponent(String name, Path executable) implements PluginComponent {
        @Override
        public int order() {
            return 50;
        }
    }

    /** Output style contributed via {@code output-styles/}. */
    record OutputStyleComponent(String name, Path styleFile) implements PluginComponent {
        @Override
        public int order() {
            return 60;
        }
    }

    /** Theme contributed via {@code themes/} (experimental — placeholder in v1.2). */
    record ThemeComponent(String name, Path themeFile) implements PluginComponent {
        @Override
        public int order() {
            return 70;
        }
    }
}
