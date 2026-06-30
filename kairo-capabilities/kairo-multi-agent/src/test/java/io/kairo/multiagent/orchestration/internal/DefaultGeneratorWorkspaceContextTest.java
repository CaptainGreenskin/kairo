/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.multiagent.orchestration.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.SharedContext;
import io.kairo.api.team.TeamStep;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Workspace-context injection closed-loop test. The coordinator gathers a {@link SharedContext}
 * once per execution and publishes it via Reactor Context; {@link DefaultGenerator#buildPrompt}
 * must filter it by the role's declared {@code ContextScope}s and inject the relevant slices into
 * the worker prompt. Before the wiring, the SharedContext SPI existed but buildPrompt never read it
 * — the L1-style "wired but silently a no-op" failure.
 */
class DefaultGeneratorWorkspaceContextTest {

    private static SharedContext sampleContext() {
        return new SharedContext.Builder()
                .workspaceTree("src/\n  Main.java\nREADME.md")
                .keyFiles(Map.of("README.md", "# Sample Project\nA demo."))
                .projectSummary("A demo project for testing.")
                .build();
    }

    private static TeamStep step(String roleId) {
        return new TeamStep(
                "s1",
                "do work",
                new RoleDefinition(roleId, roleId, "instr", "agent.default", List.of()),
                List.of(),
                0);
    }

    private static String buildPrompt(DefaultGenerator gen, TeamStep step, SharedContext ctx)
            throws Exception {
        Method m =
                DefaultGenerator.class.getDeclaredMethod(
                        "buildPrompt",
                        TeamStep.class,
                        String.class,
                        int.class,
                        List.class,
                        List.class,
                        SharedContext.class);
        m.setAccessible(true);
        return (String) m.invoke(gen, step, "goal", 1, List.of(), List.of(), ctx);
    }

    @Test
    @DisplayName("researcher (FULL_TREE+KEY_FILES) gets the workspace tree and key files")
    void researcherGetsTreeAndKeyFiles() throws Exception {
        DefaultGenerator gen =
                new DefaultGenerator(
                        new io.kairo.multiagent.subagent.ExpertRoleRegistry(), null, null);
        String prompt = buildPrompt(gen, step("expert:researcher"), sampleContext());
        assertTrue(prompt.contains("[Workspace Context]"), prompt);
        assertTrue(prompt.contains("Workspace tree:"), "researcher should see the tree");
        assertTrue(prompt.contains("Key project files:"), "researcher should see key files");
        assertTrue(prompt.contains("README.md"));
    }

    @Test
    @DisplayName("coder (SOURCE_FILES) gets the project summary but NOT the full tree")
    void coderGetsSummaryNotTree() throws Exception {
        DefaultGenerator gen =
                new DefaultGenerator(
                        new io.kairo.multiagent.subagent.ExpertRoleRegistry(), null, null);
        String prompt = buildPrompt(gen, step("expert:coder"), sampleContext());
        // SOURCE_FILES → project summary is injected.
        assertTrue(prompt.contains("Project: A demo project"), "coder should see the summary");
        // No FULL_TREE/KEY_FILES scope → tree and key files must NOT leak in.
        assertFalse(prompt.contains("Workspace tree:"), "coder must not see the full tree");
        assertFalse(prompt.contains("Key project files:"), "coder must not see key files");
    }

    @Test
    @DisplayName("empty SharedContext (no gatherer wired) → no workspace section at all")
    void emptyContextIsNoOp() throws Exception {
        DefaultGenerator gen =
                new DefaultGenerator(
                        new io.kairo.multiagent.subagent.ExpertRoleRegistry(), null, null);
        String prompt = buildPrompt(gen, step("expert:researcher"), SharedContext.empty());
        assertFalse(prompt.contains("[Workspace Context]"));
    }

    @Test
    @DisplayName("unregistered role → no scope hint → no injection (safe fallback)")
    void unregisteredRoleIsNoOp() throws Exception {
        DefaultGenerator gen =
                new DefaultGenerator(
                        new io.kairo.multiagent.subagent.ExpertRoleRegistry(), null, null);
        String prompt = buildPrompt(gen, step("expert:does-not-exist"), sampleContext());
        assertFalse(prompt.contains("[Workspace Context]"));
    }
}
