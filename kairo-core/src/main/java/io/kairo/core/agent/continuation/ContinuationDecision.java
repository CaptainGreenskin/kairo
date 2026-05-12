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
package io.kairo.core.agent.continuation;

import io.kairo.api.message.Msg;

/**
 * Outcome of a continuation strategy evaluation.
 *
 * <p>Used by the ReAct loop to determine next action when the model emits no tool calls.
 *
 * @since 0.5.0
 */
public sealed interface ContinuationDecision {

    /** Strategy has no opinion; try next one in a Composite. */
    final class Pass implements ContinuationDecision {
        /** Singleton instance. */
        public static final Pass INSTANCE = new Pass();

        private Pass() {}

        @Override
        public String toString() {
            return "Pass";
        }
    }

    /**
     * Agent loop terminates normally.
     *
     * @param reason human-readable explanation for termination
     */
    record Terminate(String reason) implements ContinuationDecision {}

    /**
     * Inject a synthetic user message and continue the loop.
     *
     * <p>The {@code syntheticUserMessage} must be tagged as internal via metadata so UI/transcript
     * filters can hide it.
     *
     * @param syntheticUserMessage the nudge message to inject
     * @param reason human-readable explanation for the nudge
     */
    record Nudge(Msg syntheticUserMessage, String reason) implements ContinuationDecision {}

    /**
     * Defer to CompactionTrigger then re-enter the loop.
     *
     * @param reason human-readable explanation for triggering compaction
     */
    record CompactAndRetry(String reason) implements ContinuationDecision {}

    /**
     * Unrecoverable — surface the cause and terminate with error.
     *
     * @param cause the underlying error
     */
    record Escalate(Throwable cause) implements ContinuationDecision {}
}
