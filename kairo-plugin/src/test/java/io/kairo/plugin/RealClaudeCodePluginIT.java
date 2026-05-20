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
import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginManifest;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import io.kairo.plugin.installer.PluginCacheManager;
import io.kairo.plugin.source.GitSubdirSourceFetcher;
import io.kairo.plugin.source.SourceFetcherRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase F.4 dogfood: clone real Claude Code plugins from {@code anthropics/claude-code}'s {@code
 * plugins/} subdirectory over the network, run them through the full {@link DefaultPluginManager}
 * pipeline (install → enable → disable → uninstall), and verify every expected component reaches
 * the plugin manifest.
 *
 * <p>Disabled by default. The {@code anthropics/claude-code} monorepo is large (~3 minutes for a
 * native shallow clone over a fast connection), so this IT is wallclock-expensive and depends on
 * upstream availability. Two ways to run:
 *
 * <ul>
 *   <li>Remove {@code @Disabled} locally and run {@code mvn -pl kairo-plugin verify
 *       -Pintegration-tests}, OR
 *   <li>Use the lighter human-friendly {@link DogfoodMain} runner and read the printed report.
 * </ul>
 *
 * <p>Even in disabled state these tests serve as executable documentation for what shape we expect
 * each upstream plugin to have. Each test uses a {@link PluginSource.GitSubdir} pointing at a real
 * plugin in the {@code anthropics/claude-code} monorepo on {@code main}.
 */
@Tag("integration")
@Disabled(
        "Requires network access + ~3 min full-clone of anthropics/claude-code per test. Run"
                + " manually: remove @Disabled and `mvn -pl kairo-plugin verify"
                + " -Pintegration-tests`. For quicker live verification use DogfoodMain.")
class RealClaudeCodePluginIT {

    private static final String REPO = "https://github.com/anthropics/claude-code.git";
    private static final String REF = "main";

    /** Generous: the monorepo full-clone takes ~3 min on a fast home connection. */
    private static final Duration FETCH_TIMEOUT = Duration.ofMinutes(8);

    @Test
    @DisplayName("commit-commands: 3 flat commands fetched from anthropics/claude-code")
    void commitCommands(@TempDir Path tmp) throws Exception {
        PluginManifest m = installAndLoad(tmp, "plugins/commit-commands");

        var commands = filterAs(m.components(), PluginComponent.CommandComponent.class);
        // The upstream tree has at least: commit, commit-push-pr, clean_gone.
        assertThat(commands)
                .extracting(PluginComponent.CommandComponent::name)
                .contains("commit", "commit-push-pr");
        // No skills, no hooks, no MCP — pure flat-command plugin.
        assertThat(filterAs(m.components(), PluginComponent.SkillComponent.class)).isEmpty();
        assertThat(filterAs(m.components(), PluginComponent.HookComponent.class)).isEmpty();
    }

    @Test
    @DisplayName("explanatory-output-style: hook with ${CLAUDE_PLUGIN_ROOT} variable")
    void explanatoryOutputStyle(@TempDir Path tmp) throws Exception {
        PluginManifest m = installAndLoad(tmp, "plugins/explanatory-output-style");

        var hooks = filterAs(m.components(), PluginComponent.HookComponent.class);
        assertThat(hooks).isNotEmpty();
        var sessionStart =
                hooks.stream()
                        .filter(h -> h.event().equals("SessionStart"))
                        .findFirst()
                        .orElseThrow();
        assertThat(sessionStart.actions()).isNotEmpty();
        var action = sessionStart.actions().get(0);
        assertThat(action.type()).isEqualTo("command");
        // The variable must be preserved verbatim through the loader.
        assertThat((String) action.config().get("command")).contains("${CLAUDE_PLUGIN_ROOT}");
    }

    @Test
    @DisplayName("frontend-design: skills/<name>/SKILL.md bundle from upstream")
    void frontendDesign(@TempDir Path tmp) throws Exception {
        PluginManifest m = installAndLoad(tmp, "plugins/frontend-design");
        var skills = filterAs(m.components(), PluginComponent.SkillComponent.class);
        assertThat(skills)
                .extracting(PluginComponent.SkillComponent::name)
                .contains("frontend-design");
    }

    @Test
    @DisplayName("pr-review-toolkit: agents + commands; agent count matches upstream tree")
    void prReviewToolkit(@TempDir Path tmp) throws Exception {
        PluginManifest m = installAndLoad(tmp, "plugins/pr-review-toolkit");
        var agents = filterAs(m.components(), PluginComponent.AgentComponent.class);
        // Upstream has 6 agents: code-reviewer, code-simplifier, comment-analyzer,
        // pr-test-analyzer, silent-failure-hunter, type-design-analyzer. Exact set is
        // upstream-mutable; assert at least the two well-known ones.
        assertThat(agents)
                .extracting(PluginComponent.AgentComponent::name)
                .contains("code-reviewer", "silent-failure-hunter");

        var commands = filterAs(m.components(), PluginComponent.CommandComponent.class);
        assertThat(commands)
                .extracting(PluginComponent.CommandComponent::name)
                .contains("review-pr");
    }

    @Test
    @DisplayName("hookify: largest plugin (hooks + commands + agents + skills) loads cleanly")
    void hookify(@TempDir Path tmp) throws Exception {
        PluginManifest m = installAndLoad(tmp, "plugins/hookify");

        // Every component family should be present.
        assertThat(filterAs(m.components(), PluginComponent.HookComponent.class)).isNotEmpty();
        assertThat(filterAs(m.components(), PluginComponent.CommandComponent.class)).isNotEmpty();
        assertThat(filterAs(m.components(), PluginComponent.AgentComponent.class)).isNotEmpty();
        assertThat(filterAs(m.components(), PluginComponent.SkillComponent.class)).isNotEmpty();

        // Hook bindings should preserve all 4 expected events.
        var hookEvents =
                filterAs(m.components(), PluginComponent.HookComponent.class).stream()
                        .map(PluginComponent.HookComponent::event)
                        .toList();
        assertThat(hookEvents).contains("PreToolUse", "PostToolUse", "Stop", "UserPromptSubmit");
    }

    @Test
    @DisplayName("End-to-end: install → enable → disable → uninstall cycles cleanly")
    void fullLifecycleAgainstRealPlugin(@TempDir Path tmp) throws Exception {
        Path data = Files.createDirectories(tmp.resolve("data"));
        var manager = newManager(tmp, data);

        var inst =
                manager.install(
                                new PluginSource.GitSubdir(REPO, REF, "plugins/commit-commands"),
                                PluginScope.USER)
                        .block(FETCH_TIMEOUT);
        assertThat(inst).isNotNull();
        assertThat(inst.metadata().name()).isEqualTo("commit-commands");

        manager.enable(inst.id()).block(Duration.ofSeconds(30));
        assertThat(manager.list().get(0).enabled()).isTrue();

        manager.disable(inst.id()).block(Duration.ofSeconds(15));
        manager.uninstall(inst.id()).block(Duration.ofSeconds(15));
        assertThat(manager.list()).isEmpty();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static DefaultPluginManager newManager(Path cacheParent, Path dataRoot)
            throws Exception {
        Path cacheRoot = Files.createDirectories(cacheParent.resolve("cache"));
        var cache = new PluginCacheManager(cacheRoot);
        var fetchers = new SourceFetcherRegistry().register(new GitSubdirSourceFetcher(cache));
        return new DefaultPluginManager(
                new DefaultPluginRegistry(),
                new PluginLoader(),
                dataRoot,
                ComponentRegistrar.noOp(),
                fetchers);
    }

    private PluginManifest installAndLoad(Path tmp, String subdir) throws Exception {
        Path data = Files.createDirectories(tmp.resolve("data"));
        var manager = newManager(tmp, data);

        PluginInstallation inst =
                manager.install(new PluginSource.GitSubdir(REPO, REF, subdir), PluginScope.USER)
                        .block(FETCH_TIMEOUT);
        assertThat(inst).as("install '%s' should succeed", subdir).isNotNull();

        // Re-load manifest from the cached directory to inspect components.
        return new PluginLoader().load(inst.rootPath(), null);
    }

    @SuppressWarnings("unchecked")
    private static <T extends PluginComponent> List<T> filterAs(
            List<PluginComponent> components, Class<T> type) {
        return components.stream().filter(type::isInstance).map(c -> (T) c).toList();
    }
}
