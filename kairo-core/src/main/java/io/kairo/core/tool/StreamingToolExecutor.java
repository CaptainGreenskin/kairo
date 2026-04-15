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
package io.kairo.core.tool;

import io.kairo.api.tool.StreamingToolResultCallback;
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.model.DetectedToolCall;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Executes tool calls as they arrive from a streaming response, respecting read/write partitioning.
 *
 * <p>Two execution strategies are available:
 *
 * <ul>
 *   <li>{@link #executeStreaming} — collects all detected tools first, then dispatches reads in
 *       parallel and writes serially.
 *   <li>{@link #executeEager} — dispatches READ_ONLY tools immediately as they arrive; WRITE and
 *       SYSTEM_CHANGE tools are queued and flushed serially when the last tool is detected.
 * </ul>
 *
 * <p>Both strategies delegate to {@link DefaultToolExecutor#executeSingle(ToolInvocation)} which
 * preserves the existing permission and approval pipeline.
 */
public class StreamingToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(StreamingToolExecutor.class);
    private final DefaultToolExecutor toolExecutor;
    private final StreamingToolResultCallback callback;

    /**
     * Create a streaming tool executor with a no-op callback.
     *
     * @param toolExecutor the underlying tool executor
     */
    public StreamingToolExecutor(DefaultToolExecutor toolExecutor) {
        this(toolExecutor, StreamingToolResultCallback.noop());
    }

    /**
     * Create a streaming tool executor with a result callback.
     *
     * @param toolExecutor the underlying tool executor
     * @param callback callback invoked when each tool completes
     */
    public StreamingToolExecutor(
            DefaultToolExecutor toolExecutor, StreamingToolResultCallback callback) {
        this.toolExecutor = toolExecutor;
        this.callback = callback;
    }

    /**
     * Execute detected tool calls after all have been collected.
     *
     * <p>READ_ONLY tools are dispatched in parallel; WRITE and SYSTEM_CHANGE tools are executed
     * serially after all reads complete.
     *
     * @param tools the detected tool calls
     * @return a Flux emitting tool results
     */
    public Flux<ToolResult> executeStreaming(Flux<DetectedToolCall> tools) {
        return tools.collectList()
                .flatMapMany(
                        toolList -> {
                            var reads = new ArrayList<DetectedToolCall>();
                            var writes = new ArrayList<DetectedToolCall>();

                            for (var tool : toolList) {
                                var sideEffect = toolExecutor.resolveSideEffect(tool.toolName());
                                if (sideEffect == ToolSideEffect.READ_ONLY) {
                                    reads.add(tool);
                                } else {
                                    writes.add(tool);
                                }
                            }

                            log.debug(
                                    "Streaming execution: {} reads (parallel), {} writes (serial)",
                                    reads.size(),
                                    writes.size());

                            // Execute reads in parallel
                            var readResults = Flux.fromIterable(reads).flatMap(this::executeSingle);

                            // Execute writes serially, after reads
                            var writeResults =
                                    Flux.fromIterable(writes).concatMap(this::executeSingle);

                            return Flux.concat(readResults, writeResults);
                        });
    }

    /**
     * Early dispatch variant: starts READ_ONLY execution immediately as tools arrive, without
     * waiting for all tools to be detected.
     *
     * <p>WRITE and SYSTEM_CHANGE tools are queued and flushed serially once the last tool in the
     * batch is detected (indicated by {@link DetectedToolCall#isLastTool()}).
     *
     * @param tools the detected tool calls as they arrive
     * @return a Flux emitting tool results
     */
    public Flux<ToolResult> executeEager(Flux<DetectedToolCall> tools) {
        var writeQueue = new ConcurrentLinkedQueue<DetectedToolCall>();

        return tools.filter(
                        tool -> {
                            if (tool.toolName() == null) {
                                log.warn(
                                        "Skipping tool call with null name (id: {})",
                                        tool.toolCallId());
                                return false;
                            }
                            return true;
                        })
                .flatMap(
                        tool -> {
                            var sideEffect = toolExecutor.resolveSideEffect(tool.toolName());
                            if (sideEffect == ToolSideEffect.READ_ONLY) {
                                // Dispatch immediately
                                log.debug("Eager dispatch READ_ONLY tool: {}", tool.toolName());
                                return executeSingle(tool);
                            } else {
                                // Queue for later serial execution
                                writeQueue.add(tool);
                                if (tool.isLastTool()) {
                                    // Last tool in batch — flush write queue serially
                                    log.debug("Flushing {} queued write tools", writeQueue.size());
                                    return Flux.fromIterable(new ArrayList<>(writeQueue))
                                            .concatMap(this::executeSingle)
                                            .doOnSubscribe(s -> writeQueue.clear());
                                }
                                return Flux.empty();
                            }
                        })
                .concatWith(
                        Mono.defer(
                                () -> {
                                    if (!writeQueue.isEmpty()) {
                                        log.debug(
                                                "Flushing {} remaining write tools on stream completion",
                                                writeQueue.size());
                                        return Flux.fromIterable(new ArrayList<>(writeQueue))
                                                .concatMap(this::executeSingle)
                                                .then(Mono.<ToolResult>empty());
                                    }
                                    return Mono.empty();
                                }));
    }

    private Mono<ToolResult> executeSingle(DetectedToolCall tool) {
        if (tool.toolName() == null || tool.toolName().isEmpty()) {
            log.warn("Skipping tool call with null/empty name: {}", tool.toolCallId());
            return Mono.just(
                    new ToolResult(tool.toolCallId(), "Tool name is null or empty", true, null));
        }
        log.debug("Executing streaming tool: {} (id: {})", tool.toolName(), tool.toolCallId());
        var invocation = new ToolInvocation(tool.toolName(), tool.args());
        return toolExecutor
                .executeSingle(invocation)
                .map(
                        result ->
                                new ToolResult(
                                        tool.toolCallId(),
                                        result.content(),
                                        result.isError(),
                                        result.metadata()))
                .doOnNext(result -> callback.onComplete(tool.toolCallId(), result));
    }
}
