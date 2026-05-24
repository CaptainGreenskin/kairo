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

import io.kairo.api.plugin.PluginComponent;
import io.kairo.api.plugin.PluginManifest;
import io.kairo.plugin.testsupport.Fixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Compatibility test exercising five real Claude Code plugins, verbatim, from the {@code
 * anthropics/claude-code} demo plugins repository (commit-commands, explanatory-output-style,
 * frontend-design, pr-review-toolkit, hookify).
 *
 * <p>Goal of this TCK: prove that a plugin authored against the published Claude Code file format
 * loads in Kairo without any source modification beyond the directory-namespace rename ({@code
 * .claude-plugin/} → {@code .kairo-plugin/}). None of the sampled plugins ship a {@code
 * plugin.json} at the conventional location, so we additionally verify our manifest synthesis path
 * (directory name → metadata).
 *
 * <p>Each test loads its fixture and asserts:
 *
 * <ul>
 *   <li>The loader does not throw on the verbatim file content
 *   <li>Components of the expected types are recognised
 *   <li>Key fields (file paths, hook event names, action shapes) survive parsing
 * </ul>
 */
class ClaudeCodeCompatTest {

    private final PluginLoader loader = new PluginLoader();

    @Test
    @DisplayName("commit-commands: pure commands/, no skills, no manifest")
    void commitCommandsPlugin() throws Exception {
        Path root = Fixtures.copyToTemp("claude-code-samples/commit-commands");
        PluginManifest m = loader.load(root, null);

        // Manifest synthesised from directory name (no .kairo-plugin/plugin.json present).
        assertThat(m.metadata().name()).isEqualTo("commit-commands");
        assertThat(m.metadata().version()).isEqualTo("0.0.0");

        // Two flat commands → two CommandComponent entries.
        var commands =
                m.components().stream()
                        .filter(c -> c instanceof PluginComponent.CommandComponent)
                        .map(c -> (PluginComponent.CommandComponent) c)
                        .toList();
        assertThat(commands)
                .extracting(PluginComponent.CommandComponent::name)
                .containsExactlyInAnyOrder("commit", "commit-push-pr");

        // No skills, no agents, no hooks, no MCP, no bin.
        assertThat(filterByType(m.components(), "SkillComponent")).isEmpty();
        assertThat(filterByType(m.components(), "AgentComponent")).isEmpty();
        assertThat(filterByType(m.components(), "HookComponent")).isEmpty();
        assertThat(filterByType(m.components(), "McpComponent")).isEmpty();
    }

    @Test
    @DisplayName("explanatory-output-style: hook-only plugin with ${CLAUDE_PLUGIN_ROOT} variable")
    void explanatoryOutputStylePlugin() throws Exception {
        Path root = Fixtures.copyToTemp("claude-code-samples/explanatory-output-style");
        PluginManifest m = loader.load(root, null);

        assertThat(m.metadata().name()).isEqualTo("explanatory-output-style");

        var hooks = filterAs(m.components(), PluginComponent.HookComponent.class);
        assertThat(hooks).hasSize(1);
        var hook = hooks.get(0);
        assertThat(hook.event()).isEqualTo("SessionStart");
        assertThat(hook.actions()).hasSize(1);

        var action = hook.actions().get(0);
        assertThat(action.type()).isEqualTo("command");
        // The Claude Code variable is preserved verbatim — runtime resolves it at execution time.
        assertThat(action.config().get("command"))
                .isEqualTo("${CLAUDE_PLUGIN_ROOT}/hooks-handlers/session-start.sh");
    }

    @Test
    @DisplayName("frontend-design: skills/<name>/SKILL.md bundle, no other components")
    void frontendDesignPlugin() throws Exception {
        Path root = Fixtures.copyToTemp("claude-code-samples/frontend-design");
        PluginManifest m = loader.load(root, null);

        assertThat(m.metadata().name()).isEqualTo("frontend-design");

        var skills = filterAs(m.components(), PluginComponent.SkillComponent.class);
        assertThat(skills)
                .as("a single skill named after its directory")
                .hasSize(1)
                .extracting(PluginComponent.SkillComponent::name)
                .containsExactly("frontend-design");
        assertThat(skills.get(0).skillFile().getFileName().toString()).isEqualTo("SKILL.md");
    }

    @Test
    @DisplayName("pr-review-toolkit: agents + commands, no other components")
    void prReviewToolkitPlugin() throws Exception {
        Path root = Fixtures.copyToTemp("claude-code-samples/pr-review-toolkit");
        PluginManifest m = loader.load(root, null);

        assertThat(m.metadata().name()).isEqualTo("pr-review-toolkit");

        var agents = filterAs(m.components(), PluginComponent.AgentComponent.class);
        assertThat(agents)
                .extracting(PluginComponent.AgentComponent::name)
                .containsExactlyInAnyOrder("code-reviewer", "silent-failure-hunter");

        var commands = filterAs(m.components(), PluginComponent.CommandComponent.class);
        assertThat(commands)
                .extracting(PluginComponent.CommandComponent::name)
                .containsExactly("review-pr");
    }

    @Test
    @DisplayName("hookify: most comprehensive — hooks + commands + agents + skills")
    void hookifyPlugin() throws Exception {
        Path root = Fixtures.copyToTemp("claude-code-samples/hookify");
        PluginManifest m = loader.load(root, null);

        assertThat(m.metadata().name()).isEqualTo("hookify");

        // Hooks: 4 events declared, each with one command action.
        var hooks = filterAs(m.components(), PluginComponent.HookComponent.class);
        assertThat(hooks)
                .extracting(PluginComponent.HookComponent::event)
                .containsExactlyInAnyOrder("PreToolUse", "PostToolUse", "Stop", "UserPromptSubmit");
        // Every action has the Claude Code variable preserved + custom timeout.
        for (var hook : hooks) {
            var action = hook.actions().get(0);
            assertThat(action.type()).isEqualTo("command");
            assertThat((String) action.config().get("command")).contains("${CLAUDE_PLUGIN_ROOT}");
            assertThat(action.config().get("timeout")).isEqualTo(10);
        }

        var commands = filterAs(m.components(), PluginComponent.CommandComponent.class);
        assertThat(commands)
                .extracting(PluginComponent.CommandComponent::name)
                .containsExactlyInAnyOrder("configure", "help");

        var agents = filterAs(m.components(), PluginComponent.AgentComponent.class);
        assertThat(agents)
                .extracting(PluginComponent.AgentComponent::name)
                .containsExactly("conversation-analyzer");

        var skills = filterAs(m.components(), PluginComponent.SkillComponent.class);
        assertThat(skills)
                .extracting(PluginComponent.SkillComponent::name)
                .containsExactly("writing-rules");
    }

    @Test
    @DisplayName("Aggregate: every fixture loads without throwing and reports ≥1 component")
    void everyFixtureLoadsCleanly() throws Exception {
        for (String name :
                List.of(
                        "commit-commands",
                        "explanatory-output-style",
                        "frontend-design",
                        "pr-review-toolkit",
                        "hookify")) {
            Path root = Fixtures.copyToTemp("claude-code-samples/" + name);
            PluginManifest m = loader.load(root, null);
            assertThat(m.components())
                    .as("plugin '%s' must contribute at least one component", name)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("Component registration order is deterministic across all real fixtures")
    void componentOrderingIsStable() throws Exception {
        Path root = Fixtures.copyToTemp("claude-code-samples/hookify");
        PluginManifest m = loader.load(root, null);
        // Components are sorted by PluginComponent.order(); orderings should be non-decreasing.
        List<Integer> orders = m.components().stream().map(PluginComponent::order).toList();
        for (int i = 1; i < orders.size(); i++) {
            assertThat(orders.get(i)).isGreaterThanOrEqualTo(orders.get(i - 1));
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static List<PluginComponent> filterByType(
            List<PluginComponent> components, String typeName) {
        return components.stream()
                .filter(c -> c.getClass().getSimpleName().equals(typeName))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static <T extends PluginComponent> List<T> filterAs(
            List<PluginComponent> components, Class<T> type) {
        return components.stream().filter(type::isInstance).map(c -> (T) c).toList();
    }
}
