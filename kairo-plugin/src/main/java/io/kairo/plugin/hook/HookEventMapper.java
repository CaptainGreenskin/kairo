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

import io.kairo.api.hook.HookPhase;
import java.util.Map;
import java.util.Optional;

/**
 * Translates the event-name string used in {@code hooks.json} (which mirrors Claude Code's plugin
 * hook event vocabulary) into a Kairo {@link HookPhase}.
 *
 * <p>Two name spaces are supported:
 *
 * <ul>
 *   <li>The Claude-Code-compatible names (e.g. {@code "PreToolUse"}, {@code "SessionStart"}) —
 *       these are the strings literally written in plugin files and what most market plugins use.
 *   <li>The Kairo-canonical enum constants (e.g. {@code "PRE_ACTING"}) — for native Kairo plugins
 *       that prefer the framework's own vocabulary.
 * </ul>
 *
 * <p>The single-source-of-truth mapping table is documented inline so any future schema drift can
 * be audited from one place.
 */
public final class HookEventMapper {

    /**
     * Compat-name → HookPhase mapping. Update this map when adding new events. Names that match a
     * Kairo enum constant directly fall through to {@link HookPhase#valueOf(String)} and don't need
     * an entry here.
     */
    private static final Map<String, HookPhase> COMPAT =
            Map.ofEntries(
                    Map.entry("SessionStart", HookPhase.SESSION_START),
                    Map.entry("SessionEnd", HookPhase.SESSION_END),
                    Map.entry("Setup", HookPhase.SETUP),
                    Map.entry("UserPromptSubmit", HookPhase.USER_PROMPT_SUBMIT),
                    Map.entry("UserPromptExpansion", HookPhase.USER_PROMPT_EXPANSION),
                    Map.entry("PreToolUse", HookPhase.PRE_ACTING),
                    Map.entry("PostToolUse", HookPhase.POST_ACTING),
                    Map.entry("PostToolUseFailure", HookPhase.POST_TOOL_FAILURE),
                    Map.entry("PostToolBatch", HookPhase.POST_TOOL_BATCH),
                    Map.entry("PermissionRequest", HookPhase.PERMISSION_REQUEST),
                    Map.entry("PermissionDenied", HookPhase.PERMISSION_DENIED),
                    Map.entry("Notification", HookPhase.NOTIFICATION),
                    Map.entry("SubagentStart", HookPhase.SUBAGENT_START),
                    Map.entry("SubagentStop", HookPhase.SUBAGENT_STOP),
                    Map.entry("TaskCreated", HookPhase.TASK_CREATED),
                    Map.entry("TaskCompleted", HookPhase.TASK_COMPLETED),
                    // Claude Code "Stop" = "agent's main loop about to return final answer" =
                    // PRE_COMPLETE.
                    Map.entry("Stop", HookPhase.PRE_COMPLETE),
                    Map.entry("StopFailure", HookPhase.STOP_FAILURE),
                    Map.entry("TeammateIdle", HookPhase.TEAMMATE_IDLE),
                    Map.entry("InstructionsLoaded", HookPhase.INSTRUCTIONS_LOADED),
                    Map.entry("ConfigChange", HookPhase.CONFIG_CHANGE),
                    Map.entry("CwdChanged", HookPhase.CWD_CHANGED),
                    Map.entry("FileChanged", HookPhase.FILE_CHANGED),
                    Map.entry("WorktreeCreate", HookPhase.WORKTREE_CREATE),
                    Map.entry("WorktreeRemove", HookPhase.WORKTREE_REMOVE),
                    Map.entry("PreCompact", HookPhase.PRE_COMPACT),
                    Map.entry("PostCompact", HookPhase.POST_COMPACT),
                    // Elicitation events have no first-class Kairo phase yet; route through
                    // NOTIFICATION
                    // as the closest user-visible domain. Plugins that depend on these exact
                    // semantics
                    // will need a follow-up SPI extension; logged at debug level by callers.
                    Map.entry("Elicitation", HookPhase.NOTIFICATION),
                    Map.entry("ElicitationResult", HookPhase.NOTIFICATION));

    private HookEventMapper() {}

    /**
     * Resolves a hook event name to a {@link HookPhase}. Tries the compat table first, then the
     * Kairo-canonical enum constant. Returns empty if neither matches.
     */
    public static Optional<HookPhase> toPhase(String eventName) {
        if (eventName == null || eventName.isBlank()) return Optional.empty();
        HookPhase compat = COMPAT.get(eventName);
        if (compat != null) return Optional.of(compat);
        try {
            return Optional.of(HookPhase.valueOf(eventName));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** Whether this event has a known mapping (either compat or canonical). */
    public static boolean isKnown(String eventName) {
        return toPhase(eventName).isPresent();
    }

    /** All compat (Claude-Code-style) names this mapper recognises. Mostly for diagnostics. */
    public static java.util.Set<String> compatNames() {
        return COMPAT.keySet();
    }
}
