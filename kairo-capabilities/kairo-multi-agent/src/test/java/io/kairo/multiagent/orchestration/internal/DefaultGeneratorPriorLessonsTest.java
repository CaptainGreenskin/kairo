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
import io.kairo.multiagent.orchestration.ExpertMemoryEntry;
import io.kairo.multiagent.orchestration.ExpertMemoryStore;
import io.kairo.multiagent.subagent.ExpertRoleRegistry;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L1 self-evolution closed-loop test (seed-marker technique, per "学习/进化类能力必须端到端验证闭环").
 *
 * <p>{@link ExpertMemoryStore} records cross-task lessons per role; {@link
 * DefaultGenerator#buildPrompt} must recall them and inject a {@code [Prior Lessons]} section into
 * the worker prompt so experts benefit from accumulated experience. This test seeds a unique-marker
 * lesson, builds a worker prompt, and asserts the marker reaches the prompt — the decisive proof
 * that the recall→inject half of the L1 loop is live (not the L1-style "wired but silently a no-op"
 * failure). It complements {@link DefaultGeneratorWorkspaceContextTest} (the I/context loop).
 */
class DefaultGeneratorPriorLessonsTest {

    private static final String ROLE = "expert:tester";
    private static final String MARKER =
            "PRIOR_LESSON_MARKER_7Q3 — confirm scope before expanding coverage";

    private static TeamStep step(String roleId) {
        return new TeamStep(
                "s1",
                "do work",
                new RoleDefinition(roleId, roleId, "instr", "agent.default", List.of()),
                List.of(),
                0);
    }

    private static String buildPrompt(DefaultGenerator gen, TeamStep step) throws Exception {
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
        return (String) m.invoke(gen, step, "goal", 1, List.of(), List.of(), SharedContext.empty());
    }

    @Test
    @DisplayName("seeded marker lesson is recalled and injected into the worker prompt")
    void seededLessonIsInjected(@TempDir Path tmp) throws Exception {
        ExpertMemoryStore store = new ExpertMemoryStore(tmp);
        store.recordLessons(
                        ROLE,
                        ROLE,
                        List.of(new ExpertMemoryEntry(ROLE, ROLE, MARKER, Instant.now(), 0.9)))
                .block();

        DefaultGenerator gen = new DefaultGenerator(new ExpertRoleRegistry(), null, store);
        String prompt = buildPrompt(gen, step(ROLE));

        assertTrue(
                prompt.contains("[Prior Lessons]"), "prompt must carry the prior-lessons section");
        assertTrue(
                prompt.contains(MARKER),
                "the seeded marker lesson must reach the worker prompt — L1 recall→inject is live");
    }

    @Test
    @DisplayName("no memory store wired → no prior-lessons section (safe no-op)")
    void noStoreIsNoOp() throws Exception {
        DefaultGenerator gen = new DefaultGenerator(new ExpertRoleRegistry(), null, null);
        String prompt = buildPrompt(gen, step(ROLE));
        assertFalse(prompt.contains("[Prior Lessons]"));
    }

    @Test
    @DisplayName("lesson recorded under a different role does NOT leak into another role's prompt")
    void lessonsAreRoleScoped(@TempDir Path tmp) throws Exception {
        ExpertMemoryStore store = new ExpertMemoryStore(tmp);
        store.recordLessons(
                        "expert:coder",
                        "expert:coder",
                        List.of(
                                new ExpertMemoryEntry(
                                        "expert:coder",
                                        "expert:coder",
                                        MARKER,
                                        Instant.now(),
                                        0.9)))
                .block();

        DefaultGenerator gen = new DefaultGenerator(new ExpertRoleRegistry(), null, store);
        // Request a tester prompt; the coder-scoped marker must not appear.
        String prompt = buildPrompt(gen, step(ROLE));
        assertFalse(
                prompt.contains(MARKER),
                "lessons are role-scoped — a coder lesson must not leak into a tester prompt");
    }
}
