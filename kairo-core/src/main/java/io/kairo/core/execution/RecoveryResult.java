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
package io.kairo.core.execution;

import io.kairo.api.message.Msg;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Result of a crash recovery operation.
 *
 * <p>Contains the rebuilt conversation history and the resume point so that the ReAct loop can
 * continue from where it left off.
 *
 * @param executionId the execution being recovered
 * @param resumeFromIteration the iteration index to resume from
 * @param rebuiltHistory the rebuilt conversation history up to the recovery point
 * @param lastToolCallCachedResult cached result of the last incomplete tool call (null if none or
 *     if the tool is idempotent and should be re-executed). Kept for backward compatibility.
 * @param requiresHumanConfirmation true if an unannotated tool had no cached result and requires
 *     human confirmation before proceeding
 * @param cachedToolResults mapping of toolCallId to cached TOOL_CALL_RESPONSE payload for all
 *     completed tool calls after the last checkpoint
 * @param interruptedToolCallIds list of toolCallIds for tool calls that were interrupted (have a
 *     TOOL_CALL_REQUEST but no matching TOOL_CALL_RESPONSE)
 * @since v0.8
 */
public record RecoveryResult(
        String executionId,
        int resumeFromIteration,
        List<Msg> rebuiltHistory,
        @Nullable String lastToolCallCachedResult,
        boolean requiresHumanConfirmation,
        Map<String, String> cachedToolResults,
        List<String> interruptedToolCallIds) {

    /** Backward-compatible constructor without toolCallId-based fields. */
    public RecoveryResult(
            String executionId,
            int resumeFromIteration,
            List<Msg> rebuiltHistory,
            @Nullable String lastToolCallCachedResult,
            boolean requiresHumanConfirmation) {
        this(
                executionId,
                resumeFromIteration,
                rebuiltHistory,
                lastToolCallCachedResult,
                requiresHumanConfirmation,
                Map.of(),
                List.of());
    }
}
