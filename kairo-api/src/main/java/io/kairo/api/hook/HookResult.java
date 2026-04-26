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

import io.kairo.api.Stable;
import io.kairo.api.message.Msg;
import java.util.Map;

/**
 * Structured result from a hook invocation that can drive agent behavior.
 *
 * <p>Unlike plain event modification, a HookResult can:
 *
 * <ul>
 *   <li>Abort the current operation (e.g., block a tool call)
 *   <li>Skip the current operation without error
 *   <li>Modify tool input parameters before execution
 *   <li>Inject a message into the conversation context
 *   <li>Inject additional context string into the conversation
 * </ul>
 *
 * @param event the (possibly modified) event
 * @param decision the hook's behavioral decision
 * @param injectedContext additional context to inject into the conversation (nullable)
 * @param modifiedInput modified tool input parameters (nullable, PreActing only)
 * @param reason human-readable reason for the decision (nullable)
 * @param injectedMessage a message to inject into conversation history (nullable, INJECT only)
 * @param hookSource identifier of the hook that produced this result (nullable)
 * @param <T> the event type
 */
@Stable(
        value = "Hook behavioral result record; Decision enum + static factories frozen since v0.6",
        since = "1.0.0")
public record HookResult<T>(
        T event,
        Decision decision,
        String injectedContext,
        Map<String, Object> modifiedInput,
        String reason,
        Msg injectedMessage,
        String hookSource) {

    /** Hook behavioral decisions, ordered by merge priority (higher ordinal wins). */
    public enum Decision {
        /** Continue normal execution. Priority 0. */
        CONTINUE(0),
        /** Continue and inject message into context. Priority 1. */
        INJECT(1),
        /** Continue with modified parameters. Priority 2. */
        MODIFY(2),
        /** Skip current operation, continue loop (no error). Priority 3. */
        SKIP(3),
        /** Terminate entire agent. Priority 4. */
        ABORT(4);

        private final int priority;

        Decision(int priority) {
            this.priority = priority;
        }

        /** The merge priority of this decision. Higher wins. */
        public int priority() {
            return priority;
        }
    }

    /** Create a result that continues normal execution. */
    public static <T> HookResult<T> proceed(T event) {
        return new HookResult<>(event, Decision.CONTINUE, null, null, null, null, null);
    }

    /** Create a result that aborts the current operation. */
    public static <T> HookResult<T> abort(T event, String reason) {
        return new HookResult<>(event, Decision.ABORT, null, null, reason, null, null);
    }

    /** Create a result that modifies tool input. */
    public static <T> HookResult<T> modify(T event, Map<String, Object> modifiedInput) {
        return new HookResult<>(event, Decision.MODIFY, null, modifiedInput, null, null, null);
    }

    /** Create a result with injected context. */
    public static <T> HookResult<T> withContext(T event, String context) {
        return new HookResult<>(event, Decision.CONTINUE, context, null, null, null, null);
    }

    /** Create a result that skips the current operation. */
    public static <T> HookResult<T> skip(T event, String reason) {
        return new HookResult<>(event, Decision.SKIP, null, null, reason, null, null);
    }

    /** Create a result that injects a message into the conversation. */
    public static <T> HookResult<T> inject(T event, Msg message, String source) {
        return new HookResult<>(event, Decision.INJECT, null, null, null, message, source);
    }

    /** Whether this result allows the operation to proceed (not ABORT). */
    public boolean shouldProceed() {
        return decision != Decision.ABORT;
    }

    /** Whether the operation should be skipped. */
    public boolean shouldSkip() {
        return decision == Decision.SKIP;
    }

    /** Whether this result has modified input. */
    public boolean hasModifiedInput() {
        return modifiedInput != null && !modifiedInput.isEmpty();
    }

    /** Whether this result has context to inject. */
    public boolean hasInjectedContext() {
        return injectedContext != null && !injectedContext.isEmpty();
    }

    /** Whether this result has an injected message. */
    public boolean hasInjectedMessage() {
        return injectedMessage != null;
    }
}
