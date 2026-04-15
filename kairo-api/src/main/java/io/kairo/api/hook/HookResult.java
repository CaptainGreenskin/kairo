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

import java.util.Map;

/**
 * Structured result from a hook invocation that can drive agent behavior.
 *
 * <p>Unlike plain event modification, a HookResult can:
 *
 * <ul>
 *   <li>Abort the current operation (e.g., block a tool call)
 *   <li>Modify tool input parameters before execution
 *   <li>Inject additional context into the conversation
 * </ul>
 *
 * @param event the (possibly modified) event
 * @param decision the hook's behavioral decision
 * @param injectedContext additional context to inject into the conversation (nullable)
 * @param modifiedInput modified tool input parameters (nullable, PreActing only)
 * @param reason human-readable reason for the decision (nullable)
 * @param <T> the event type
 */
public record HookResult<T>(
        T event,
        Decision decision,
        String injectedContext,
        Map<String, Object> modifiedInput,
        String reason) {

    /** Hook behavioral decisions. */
    public enum Decision {
        /** Continue normal execution. */
        CONTINUE,
        /** Abort the current operation. */
        ABORT,
        /** Continue with modified parameters. */
        MODIFY
    }

    /** Create a result that continues normal execution. */
    public static <T> HookResult<T> proceed(T event) {
        return new HookResult<>(event, Decision.CONTINUE, null, null, null);
    }

    /** Create a result that aborts the current operation. */
    public static <T> HookResult<T> abort(T event, String reason) {
        return new HookResult<>(event, Decision.ABORT, null, null, reason);
    }

    /** Create a result that modifies tool input. */
    public static <T> HookResult<T> modify(T event, Map<String, Object> modifiedInput) {
        return new HookResult<>(event, Decision.MODIFY, null, modifiedInput, null);
    }

    /** Create a result with injected context. */
    public static <T> HookResult<T> withContext(T event, String context) {
        return new HookResult<>(event, Decision.CONTINUE, context, null, null);
    }

    /** Whether this result allows the operation to proceed. */
    public boolean shouldProceed() {
        return decision != Decision.ABORT;
    }

    /** Whether this result has modified input. */
    public boolean hasModifiedInput() {
        return modifiedInput != null && !modifiedInput.isEmpty();
    }

    /** Whether this result has context to inject. */
    public boolean hasInjectedContext() {
        return injectedContext != null && !injectedContext.isEmpty();
    }
}
