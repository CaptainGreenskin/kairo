/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.hook;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.hook.HookPhase;
import org.junit.jupiter.api.Test;

class HookEventMapperTest {

    @Test
    void mapsCommonClaudeCodeEvents() {
        assertThat(HookEventMapper.toPhase("PreToolUse")).contains(HookPhase.PRE_ACTING);
        assertThat(HookEventMapper.toPhase("PostToolUse")).contains(HookPhase.POST_ACTING);
        assertThat(HookEventMapper.toPhase("SessionStart")).contains(HookPhase.SESSION_START);
        assertThat(HookEventMapper.toPhase("SessionEnd")).contains(HookPhase.SESSION_END);
        assertThat(HookEventMapper.toPhase("UserPromptSubmit"))
                .contains(HookPhase.USER_PROMPT_SUBMIT);
    }

    @Test
    void stopMapsToPreComplete() {
        // Semantic: Claude Code's "Stop" fires when the main loop is about to return; this matches
        // Kairo's PRE_COMPLETE which is the "agent about to finalise its answer" hook.
        assertThat(HookEventMapper.toPhase("Stop")).contains(HookPhase.PRE_COMPLETE);
    }

    @Test
    void compactPhasesMap() {
        assertThat(HookEventMapper.toPhase("PreCompact")).contains(HookPhase.PRE_COMPACT);
        assertThat(HookEventMapper.toPhase("PostCompact")).contains(HookPhase.POST_COMPACT);
    }

    @Test
    void worktreeEventsMap() {
        assertThat(HookEventMapper.toPhase("WorktreeCreate")).contains(HookPhase.WORKTREE_CREATE);
        assertThat(HookEventMapper.toPhase("WorktreeRemove")).contains(HookPhase.WORKTREE_REMOVE);
    }

    @Test
    void permissionEventsMap() {
        assertThat(HookEventMapper.toPhase("PermissionRequest"))
                .contains(HookPhase.PERMISSION_REQUEST);
        assertThat(HookEventMapper.toPhase("PermissionDenied"))
                .contains(HookPhase.PERMISSION_DENIED);
    }

    @Test
    void taskAndSubagentEventsMap() {
        assertThat(HookEventMapper.toPhase("TaskCreated")).contains(HookPhase.TASK_CREATED);
        assertThat(HookEventMapper.toPhase("TaskCompleted")).contains(HookPhase.TASK_COMPLETED);
        assertThat(HookEventMapper.toPhase("SubagentStart")).contains(HookPhase.SUBAGENT_START);
        assertThat(HookEventMapper.toPhase("SubagentStop")).contains(HookPhase.SUBAGENT_STOP);
    }

    @Test
    void elicitationFallsBackToNotification() {
        // Documented best-effort routing; tracks tech-debt for an SPI extension.
        assertThat(HookEventMapper.toPhase("Elicitation")).contains(HookPhase.NOTIFICATION);
        assertThat(HookEventMapper.toPhase("ElicitationResult")).contains(HookPhase.NOTIFICATION);
    }

    @Test
    void kairoCanonicalNameAlsoWorks() {
        // Native Kairo plugins can use the enum constant directly.
        assertThat(HookEventMapper.toPhase("PRE_ACTING")).contains(HookPhase.PRE_ACTING);
        assertThat(HookEventMapper.toPhase("POST_ACTING")).contains(HookPhase.POST_ACTING);
        assertThat(HookEventMapper.toPhase("SESSION_START")).contains(HookPhase.SESSION_START);
    }

    @Test
    void unknownEventReturnsEmpty() {
        assertThat(HookEventMapper.toPhase("DoesNotExist")).isEmpty();
        assertThat(HookEventMapper.toPhase("")).isEmpty();
        assertThat(HookEventMapper.toPhase(null)).isEmpty();
    }

    @Test
    void isKnownReportsCoverage() {
        assertThat(HookEventMapper.isKnown("PreToolUse")).isTrue();
        assertThat(HookEventMapper.isKnown("PRE_ACTING")).isTrue();
        assertThat(HookEventMapper.isKnown("Bogus")).isFalse();
    }

    @Test
    void compatNamesCoverAllHookPhasesEitherWay() {
        // Sanity guard: every HookPhase should be reachable via at least one compat name OR
        // by its canonical enum name. If a new phase is added without a compat entry, this test
        // serves as a reminder to consider naming for plugin-file authors.
        for (HookPhase phase : HookPhase.values()) {
            boolean reachable =
                    HookEventMapper.toPhase(phase.name()).isPresent()
                            || HookEventMapper.compatNames().stream()
                                    .anyMatch(
                                            n -> HookEventMapper.toPhase(n).orElseThrow() == phase);
            // PRE_COMPLETE is reached via "Stop", others must round-trip via their enum name.
            assertThat(reachable).as("HookPhase " + phase + " must be reachable").isTrue();
        }
    }
}
