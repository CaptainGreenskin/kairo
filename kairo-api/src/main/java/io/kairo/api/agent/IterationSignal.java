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
package io.kairo.api.agent;

import io.kairo.api.Stable;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import java.util.List;

/**
 * Represents the outcome of a single ReAct iteration. Each case is a structured signal that the
 * {@code ReActLoop} dispatcher interprets to decide the next action — eliminating opaque
 * continuation thunks.
 *
 * <p>Sealed: the compiler enforces exhaustive switch in the dispatcher.
 *
 * @since 1.3.0
 */
@Stable(since = "1.3.0")
public sealed interface IterationSignal {

    /** This iteration produced a final answer; the loop terminates. */
    record Complete(Msg finalAnswer) implements IterationSignal {}

    /** The model requested tool calls — dispatcher will run guard + execute. */
    record ToolCallsRequested(List<Content.ToolUseContent> calls) implements IterationSignal {}

    /** Tools finished executing; continue to next iteration. */
    record ContinueAfterTools(int toolCallCount) implements IterationSignal {}

    /** No tool calls but strategy decided to nudge; loop continues. */
    record ContinueWithNudge(Msg syntheticMsg, String reason) implements IterationSignal {}

    /** Strategy requires compaction before continuing. */
    record CompactThenContinue(String reason) implements IterationSignal {}

    /** Nothing happened this iteration (hook veto / loop rescue / loop warn). */
    record Skip(String reason) implements IterationSignal {}

    /** Loop detection hard-stop triggered (expected safety mechanism, distinct from Abort). */
    record LoopDetected(LoopDetectionInfo info) implements IterationSignal {}

    /** Unrecoverable error; loop terminates with error. */
    record Abort(Throwable cause, String reason) implements IterationSignal {}

    /**
     * Pure data carrier for loop detection results (no reference to kairo-core package-private
     * types).
     */
    record LoopDetectionInfo(String message, String level) {}
}
