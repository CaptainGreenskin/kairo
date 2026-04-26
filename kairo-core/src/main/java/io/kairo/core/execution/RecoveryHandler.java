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

import io.kairo.api.execution.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.*;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handles crash recovery for durable executions.
 *
 * <p>Core responsibility: load a failed/incomplete {@link DurableExecution}, rebuild conversation
 * state from the event log, and produce a {@link RecoveryResult} that the ReAct loop can use to
 * resume.
 *
 * <p>Recovery protocol (per ADR-011):
 *
 * <ol>
 *   <li>Load execution from store
 *   <li>Verify status is {@link ExecutionStatus#RUNNING} or {@link ExecutionStatus#RECOVERING}
 *   <li>Update status to {@link ExecutionStatus#RECOVERING}
 *   <li>Find latest {@link ExecutionEventType#ITERATION_COMPLETE} event
 *   <li>Replay events up to that point to rebuild conversation history
 *   <li>For the last incomplete tool call (if any): check idempotency annotation to decide replay
 *       vs. cached
 *   <li>Return {@link RecoveryResult} with rebuilt state and resume point
 * </ol>
 *
 * @since v0.8
 */
public class RecoveryHandler {

    private static final Logger log = LoggerFactory.getLogger(RecoveryHandler.class);

    private final DurableExecutionStore store;

    /**
     * Create a new recovery handler.
     *
     * @param store the durable execution store to recover from
     */
    public RecoveryHandler(DurableExecutionStore store) {
        this.store = store;
    }

    /**
     * Attempt to recover a pending execution.
     *
     * <p>Returns {@link Mono#empty()} if the execution does not exist or is already in a terminal
     * state ({@link ExecutionStatus#COMPLETED} or {@link ExecutionStatus#FAILED}).
     *
     * @param executionId the execution to recover
     * @return the recovery result, or empty if recovery is not applicable
     */
    public Mono<RecoveryResult> recover(String executionId) {
        return store.recover(executionId)
                .flatMap(
                        execution -> {
                            // Only recover RUNNING or RECOVERING executions
                            if (execution.status() != ExecutionStatus.RUNNING
                                    && execution.status() != ExecutionStatus.RECOVERING) {
                                log.debug(
                                        "Execution {} has status {}, skipping recovery",
                                        executionId,
                                        execution.status());
                                return Mono.empty();
                            }

                            // Transition to RECOVERING
                            return store.updateStatus(
                                            executionId,
                                            ExecutionStatus.RECOVERING,
                                            execution.version())
                                    .then(Mono.defer(() -> rebuildState(execution)));
                        });
    }

    /**
     * Check for any pending executions and recover them.
     *
     * <p>Individual recovery failures are logged and swallowed so that one corrupted execution does
     * not prevent others from recovering.
     *
     * @return a stream of recovery results
     */
    public Flux<RecoveryResult> recoverAllPending() {
        return store.listPending()
                .flatMap(
                        exec ->
                                recover(exec.executionId())
                                        .onErrorResume(
                                                e -> {
                                                    log.warn(
                                                            "Failed to recover execution {}: {}",
                                                            exec.executionId(),
                                                            e.getMessage());
                                                    return Mono.empty();
                                                }));
    }

    /**
     * Rebuild conversation state from the event log.
     *
     * @param execution the execution to rebuild from
     * @return the recovery result
     */
    private Mono<RecoveryResult> rebuildState(DurableExecution execution) {
        return Mono.fromCallable(
                () -> {
                    List<ExecutionEvent> events = execution.events();
                    String executionId = execution.executionId();

                    // Verify hash chain integrity before rebuilding state
                    HashChainUtils.verifyChain(events);

                    // Find the latest ITERATION_COMPLETE event
                    int latestIterationCompleteIdx = -1;
                    int resumeIteration = 0;

                    for (int i = events.size() - 1; i >= 0; i--) {
                        if (events.get(i).eventType() == ExecutionEventType.ITERATION_COMPLETE) {
                            latestIterationCompleteIdx = i;
                            // Parse iteration number from payload
                            resumeIteration = parseIteration(events.get(i).payloadJson()) + 1;
                            break;
                        }
                    }

                    // Rebuild conversation history from events up to (inclusive) the latest
                    // ITERATION_COMPLETE
                    List<Msg> rebuiltHistory = new ArrayList<>();
                    int replayUpTo =
                            latestIterationCompleteIdx >= 0
                                    ? latestIterationCompleteIdx
                                    : events.size() - 1;

                    for (int i = 0; i <= replayUpTo && i < events.size(); i++) {
                        ExecutionEvent event = events.get(i);
                        Msg msg = eventToMsg(event);
                        if (msg != null) {
                            rebuiltHistory.add(msg);
                        }
                    }

                    // Check for incomplete tool calls after the last ITERATION_COMPLETE
                    // using toolCallId-based correlation
                    ToolCallCorrelation correlation =
                            correlateToolCalls(events, latestIterationCompleteIdx);

                    // Backward-compat: pick first cached result for lastToolCallCachedResult
                    String cachedToolResult = correlation.firstCachedResult();
                    boolean requiresConfirmation = !correlation.interruptedToolCallIds().isEmpty();

                    log.info(
                            "Recovery for execution {}: resume from iteration {}, "
                                    + "{} messages rebuilt, cached tool results: {}, interrupted: {}",
                            executionId,
                            resumeIteration,
                            rebuiltHistory.size(),
                            correlation.cachedResults().size(),
                            correlation.interruptedToolCallIds().size());

                    return new RecoveryResult(
                            executionId,
                            resumeIteration,
                            List.copyOf(rebuiltHistory),
                            cachedToolResult,
                            requiresConfirmation,
                            Map.copyOf(correlation.cachedResults()),
                            List.copyOf(correlation.interruptedToolCallIds()));
                });
    }

    /**
     * Convert an execution event to a conversation message.
     *
     * @return a Msg, or null if the event type does not map to a conversation message
     */
    @Nullable
    private static Msg eventToMsg(ExecutionEvent event) {
        return switch (event.eventType()) {
            case MODEL_CALL_RESPONSE ->
                    Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .addContent(new Content.TextContent(event.payloadJson()))
                            .build();
            case TOOL_CALL_RESPONSE ->
                    Msg.builder()
                            .role(MsgRole.TOOL)
                            .addContent(new Content.TextContent(event.payloadJson()))
                            .build();
            default -> null;
        };
    }

    /**
     * Correlate TOOL_CALL_REQUEST and TOOL_CALL_RESPONSE events after the last checkpoint using
     * toolCallId.
     *
     * <p>Builds a map of toolCallId → TOOL_CALL_RESPONSE payload for completed calls, and
     * identifies interrupted calls (those with a REQUEST but no matching RESPONSE).
     */
    private static ToolCallCorrelation correlateToolCalls(
            List<ExecutionEvent> events, int lastIterationCompleteIdx) {
        int startFrom = lastIterationCompleteIdx + 1;

        // Collect all REQUEST toolCallIds in order
        List<String> requestToolCallIds = new ArrayList<>();
        Map<String, String> requestPayloads = new LinkedHashMap<>();
        Map<String, String> responsePayloads = new LinkedHashMap<>();

        for (int i = startFrom; i < events.size(); i++) {
            ExecutionEvent event = events.get(i);
            if (event.eventType() == ExecutionEventType.TOOL_CALL_REQUEST) {
                String toolCallId = parseToolCallId(event.payloadJson());
                if (toolCallId != null) {
                    requestToolCallIds.add(toolCallId);
                    requestPayloads.put(toolCallId, event.payloadJson());
                } else {
                    // Legacy event without toolCallId — use positional fallback key
                    String fallbackId = "_pos_" + i;
                    requestToolCallIds.add(fallbackId);
                    requestPayloads.put(fallbackId, event.payloadJson());
                }
            } else if (event.eventType() == ExecutionEventType.TOOL_CALL_RESPONSE) {
                String toolCallId = parseToolCallId(event.payloadJson());
                if (toolCallId != null) {
                    responsePayloads.put(toolCallId, event.payloadJson());
                } else {
                    // Legacy RESPONSE without toolCallId — try positional matching
                    // Find first unmatched request
                    for (String reqId : requestToolCallIds) {
                        if (!responsePayloads.containsKey(reqId)) {
                            responsePayloads.put(reqId, event.payloadJson());
                            break;
                        }
                    }
                }
            }
        }

        // Log any RESPONSE toolCallIds that don't match any REQUEST
        for (String respId : responsePayloads.keySet()) {
            if (!requestPayloads.containsKey(respId)) {
                log.warn(
                        "TOOL_CALL_RESPONSE with toolCallId '{}' has no matching REQUEST — ignoring",
                        respId);
            }
        }

        // Build cached results (only for matched pairs)
        Map<String, String> cachedResults = new LinkedHashMap<>();
        List<String> interruptedToolCallIds = new ArrayList<>();

        for (String reqId : requestToolCallIds) {
            String responsePayload = responsePayloads.get(reqId);
            if (responsePayload != null) {
                cachedResults.put(reqId, responsePayload);
            } else {
                interruptedToolCallIds.add(reqId);
            }
        }

        return new ToolCallCorrelation(cachedResults, interruptedToolCallIds);
    }

    /**
     * Parse the toolCallId from a TOOL_CALL_REQUEST or TOOL_CALL_RESPONSE payload.
     *
     * <p>Expected format: {@code {"toolCallId":"...", ...}}
     *
     * @return the toolCallId, or null if not found
     */
    @Nullable
    static String parseToolCallId(String payloadJson) {
        if (payloadJson == null) {
            return null;
        }
        int idx = payloadJson.indexOf("\"toolCallId\":\"");
        if (idx < 0) {
            return null;
        }
        int start = idx + "\"toolCallId\":\"".length();
        int end = payloadJson.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return payloadJson.substring(start, end);
    }

    /** Internal correlation result holder. */
    record ToolCallCorrelation(
            Map<String, String> cachedResults, List<String> interruptedToolCallIds) {

        /** Return the first cached result payload, or null if none. */
        @Nullable
        String firstCachedResult() {
            return cachedResults.isEmpty() ? null : cachedResults.values().iterator().next();
        }
    }

    /**
     * Find a cached tool call result in events after the last ITERATION_COMPLETE. Kept for backward
     * compatibility — delegates to correlation-based matching.
     *
     * @return the cached result payload, or null if not found
     */
    @Nullable
    private static String findCachedToolResult(
            List<ExecutionEvent> events, int lastIterationCompleteIdx) {
        return correlateToolCalls(events, lastIterationCompleteIdx).firstCachedResult();
    }

    /**
     * Check whether there's an incomplete tool call (TOOL_CALL_REQUEST without a matching
     * TOOL_CALL_RESPONSE) after the last ITERATION_COMPLETE and no cached result is available.
     */
    private static boolean hasIncompleteToolCallWithoutCache(
            List<ExecutionEvent> events, int lastIterationCompleteIdx) {
        return !correlateToolCalls(events, lastIterationCompleteIdx)
                .interruptedToolCallIds()
                .isEmpty();
    }

    /**
     * Parse the iteration number from an ITERATION_COMPLETE payload.
     *
     * <p>Expected format: {@code {"iteration":N}}
     */
    private static int parseIteration(String payloadJson) {
        // Simple parsing — avoid Jackson dependency for this small payload
        int idx = payloadJson.indexOf("\"iteration\":");
        if (idx < 0) {
            return 0;
        }
        int start = idx + "\"iteration\":".length();
        int end = start;
        while (end < payloadJson.length()
                && (Character.isDigit(payloadJson.charAt(end)) || payloadJson.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(payloadJson.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
