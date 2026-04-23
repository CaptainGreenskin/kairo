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
@Experimental("Unified hook dispatch — contract may change in v0.11")
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
    TOOL_RESULT
}
