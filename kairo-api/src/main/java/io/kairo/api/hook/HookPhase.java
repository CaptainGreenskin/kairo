/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.api.hook;

import io.kairo.api.Experimental;

/**
 * Lifecycle phases at which a hook handler may fire. Replaces the 1-annotation-per-phase pattern
 * that lived in {@code kairo-api/hook} before v0.10; the {@link HookHandler} annotation pairs an
 * ordinary method with one of these phases.
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Unified hook dispatch — contract may change before v1.2.0 stabilization")
public enum HookPhase {
    /** Fired when an agent session begins. */
    SESSION_START,
    /** Fired when an agent session ends (success or failure). */
    SESSION_END,
    /** Fired before each reasoning (model) call. */
    PRE_REASONING,
    /** Fired after a reasoning response arrives. */
    POST_REASONING,
    /** Fired before a tool invocation begins. */
    PRE_ACTING,
    /** Fired after a tool invocation completes. */
    POST_ACTING,
    /** Fired before context compaction begins. */
    PRE_COMPACT,
    /** Fired after context compaction completes. */
    POST_COMPACT,
    /** Fired when a tool produces a result. */
    TOOL_RESULT,
    /**
     * Fired when the model response contains no tool calls (agent about to return a final answer).
     * Hooks returning INJECT force another iteration; analogous to claude-code preventContinuation.
     */
    PRE_COMPLETE,

    // ── User interaction domain ─────────────────────────────────────────────

    /** Fired when the user submits a prompt, before the agent processes it. */
    USER_PROMPT_SUBMIT,
    /** Fired when a user-typed command expands into a prompt, before it reaches the agent. */
    USER_PROMPT_EXPANSION,
    /** Fired when the agent sends a notification (e.g. idle prompt, permission prompt). */
    NOTIFICATION,

    // ── Permission domain ───────────────────────────────────────────────────

    /** Fired when a permission dialog appears (tool needs user approval). */
    PERMISSION_REQUEST,
    /** Fired when a tool call is denied by the approval classifier. */
    PERMISSION_DENIED,

    // ── Tool domain (extended) ──────────────────────────────────────────────

    /** Fired after a tool call fails (distinct from POST_ACTING which fires on success). */
    POST_TOOL_FAILURE,
    /** Fired after a full batch of parallel tool calls resolves. */
    POST_TOOL_BATCH,

    // ── Sub-agent domain ────────────────────────────────────────────────────

    /** Fired when a sub-agent is spawned. */
    SUBAGENT_START,
    /** Fired when a sub-agent finishes. */
    SUBAGENT_STOP,

    // ── Multi-agent collaboration domain ────────────────────────────────────

    /** Fired when a teammate agent goes idle between turns. */
    TEAMMATE_IDLE,
    /** Fired when a task is being created. */
    TASK_CREATED,
    /** Fired when a task is being marked as completed. */
    TASK_COMPLETED,

    // ── Environment domain ──────────────────────────────────────────────────

    /** Fired during initialization or maintenance setup. */
    SETUP,
    /** Fired when a configuration file changes during a session. */
    CONFIG_CHANGE,
    /** Fired when the working directory changes. */
    CWD_CHANGED,
    /** Fired when a watched file changes on disk. */
    FILE_CHANGED,

    // ── Lifecycle (extended) ────────────────────────────────────────────────

    /** Fired when system instructions (e.g. CLAUDE.md) are loaded into context. */
    INSTRUCTIONS_LOADED,
    /** Fired when the turn ends due to an API error. */
    STOP_FAILURE,

    // ── Worktree domain ─────────────────────────────────────────────────────

    /** Fired when a worktree is being created. */
    WORKTREE_CREATE,
    /** Fired when a worktree is being removed. */
    WORKTREE_REMOVE
}
