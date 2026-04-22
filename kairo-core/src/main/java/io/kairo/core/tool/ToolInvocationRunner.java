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

import io.kairo.api.agent.CancellationSignal;
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Executes a single tool handler with timeout, cooperative cancellation, and shutdown guard.
 *
 * <p>Extracted from {@link DefaultToolExecutor} pipeline. Contains the core execution logic
 * including {@code withCooperativeCancellation}, {@code cancellationTrigger}, and error handling.
 */
public final class ToolInvocationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolInvocationRunner.class);

    /** Reactor Context key used to propagate {@link ToolContext} through the reactive pipeline. */
    public static final Class<ToolContext> CONTEXT_KEY = ToolContext.class;

    private final Tracer tracer;
    private final GracefulShutdownManager shutdownManager;

    /**
     * Create a new invocation runner.
     *
     * @param tracer the tracer (must not be null)
     * @param shutdownManager the shutdown manager (must not be null)
     */
    public ToolInvocationRunner(Tracer tracer, GracefulShutdownManager shutdownManager) {
        this.tracer = tracer;
        this.shutdownManager = shutdownManager;
    }

    /**
     * Execute a tool handler with timeout, cooperative cancellation, and shutdown guard.
     *
     * <p>The {@code onErrorResume} block checks {@link #isCancellationException(Throwable)} FIRST
     * and propagates {@link AgentInterruptedException} via {@code Mono.error(e)}. Only other
     * exceptions are converted to error results.
     *
     * @param toolName the tool name (for error messages)
     * @param handler the tool handler to invoke
     * @param input the tool input parameters
     * @param timeout the maximum execution duration
     * @return a Mono emitting the tool result
     */
    public Mono<ToolResult> execute(
            String toolName, ToolHandler handler, Map<String, Object> input, Duration timeout) {
        Mono<ToolResult> execution =
                Mono.deferContextual(
                                innerCtxView -> {
                                    ToolContext ctx =
                                            innerCtxView.hasKey(CONTEXT_KEY)
                                                    ? innerCtxView.get(CONTEXT_KEY)
                                                    : null;
                                    if (ctx == null) {
                                        ctx = new ToolContext(null, null, Map.of());
                                    }
                                    ToolContext effective = ctx;
                                    return Mono.fromCallable(
                                            () -> handler.execute(input, effective));
                                })
                        .subscribeOn(Schedulers.boundedElastic())
                        .transform(this::withCooperativeCancellation)
                        .timeout(timeout)
                        .onErrorResume(
                                e -> {
                                    if (isCancellationException(e)) {
                                        return Mono.error(e);
                                    }
                                    if (e instanceof java.util.concurrent.TimeoutException) {
                                        return Mono.just(
                                                ToolResultSanitizer.errorResult(
                                                        toolName,
                                                        "Tool execution timed out after "
                                                                + timeout.getSeconds()
                                                                + "s"));
                                    }
                                    log.error("Tool '{}' execution failed", toolName, e);
                                    return Mono.just(
                                            ToolResultSanitizer.errorResult(
                                                    toolName, "Error: " + e.getMessage()));
                                });

        // Race against shutdown signal (shutdown guard pattern)
        Mono<ToolResult> shutdownGuard =
                shutdownManager
                        .getShutdownSignal()
                        .then(
                                Mono.just(
                                        ToolResultSanitizer.errorResult(
                                                toolName, "Tool aborted due to system shutdown")));
        return Mono.firstWithSignal(execution, shutdownGuard);
    }

    private <T> Mono<T> withCooperativeCancellation(Mono<T> source) {
        return Mono.deferContextual(
                contextView -> {
                    if (!contextView.hasKey(CancellationSignal.CONTEXT_KEY)) {
                        return source;
                    }
                    Object raw = contextView.get(CancellationSignal.CONTEXT_KEY);
                    if (!(raw instanceof CancellationSignal signal)) {
                        return source;
                    }
                    return source.takeUntilOther(cancellationTrigger(signal))
                            .switchIfEmpty(
                                    Mono.defer(
                                            () ->
                                                    signal.isCancelled()
                                                            ? Mono.error(
                                                                    new AgentInterruptedException(
                                                                            "Tool execution"
                                                                                    + " cancelled"))
                                                            : Mono.empty()));
                });
    }

    private Mono<Long> cancellationTrigger(CancellationSignal signal) {
        if (signal.isCancelled()) {
            return Mono.just(0L);
        }
        return Flux.interval(Duration.ofMillis(50)).filter(tick -> signal.isCancelled()).next();
    }

    /**
     * Check if the given exception represents a cancellation that should propagate instead of being
     * converted to an error result.
     */
    static boolean isCancellationException(Throwable e) {
        if (e instanceof AgentInterruptedException) {
            return true;
        }
        if (e instanceof java.util.concurrent.CancellationException) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause != null
                && (cause instanceof AgentInterruptedException
                        || cause instanceof java.util.concurrent.CancellationException);
    }
}
