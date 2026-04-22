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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.CancellationSignal;
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ToolInvocationRunnerTest {

    private ToolInvocationRunner runner;
    private GracefulShutdownManager shutdownManager;

    @BeforeEach
    void setUp() {
        // Use a no-op Tracer (interface has all default methods)
        Tracer tracer = new Tracer() {};
        shutdownManager = new GracefulShutdownManager();
        runner = new ToolInvocationRunner(tracer, shutdownManager);
    }

    // ===== Successful execution =====

    @Test
    void execute_successfulToolReturnsResult() {
        ToolHandler handler =
                input -> new ToolResult("echo", "echoed: " + input.get("msg"), false, Map.of());

        StepVerifier.create(
                        runner.execute(
                                "echo", handler, Map.of("msg", "hello"), Duration.ofSeconds(5)))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("echoed: hello", result.content());
                        })
                .verifyComplete();
    }

    // ===== Exception handling =====

    @Test
    void execute_toolThrowingException_returnsErrorResult() {
        ToolHandler handler =
                input -> {
                    throw new RuntimeException("boom");
                };

        StepVerifier.create(runner.execute("failing", handler, Map.of(), Duration.ofSeconds(5)))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("boom"));
                        })
                .verifyComplete();
    }

    // ===== Timeout =====

    @Test
    void execute_timeout_returnsErrorResult() {
        ToolHandler handler =
                input -> {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new ToolResult("slow", "done", false, Map.of());
                };

        StepVerifier.create(runner.execute("slow", handler, Map.of(), Duration.ofSeconds(1)))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("timed out"));
                        })
                .verifyComplete();
    }

    // ===== Cancellation propagation =====

    @Test
    void execute_agentInterruptedException_propagatesAsError() {
        ToolHandler handler =
                input -> {
                    throw new AgentInterruptedException("cancelled");
                };

        StepVerifier.create(runner.execute("cancel", handler, Map.of(), Duration.ofSeconds(5)))
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("cancelled"))
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void execute_cooperativeCancellation_propagatesAgentInterruptedException() {
        ToolHandler handler =
                input -> {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new ToolResult("slow", "done", false, Map.of());
                };

        AtomicBoolean cancelled = new AtomicBoolean(false);
        CancellationSignal signal = cancelled::get;

        // Fire cancellation after 200ms
        Mono.delay(Duration.ofMillis(200)).doOnNext(tick -> cancelled.set(true)).subscribe();

        Mono<ToolResult> execution =
                runner.execute("slow", handler, Map.of(), Duration.ofSeconds(10))
                        .contextWrite(ctx -> ctx.put(CancellationSignal.CONTEXT_KEY, signal));

        StepVerifier.create(execution)
                .expectErrorMatches(e -> e instanceof AgentInterruptedException)
                .verify(Duration.ofSeconds(5));
    }

    // ===== isCancellationException =====

    @Test
    void isCancellationException_agentInterrupted() {
        assertTrue(
                ToolInvocationRunner.isCancellationException(
                        new AgentInterruptedException("test")));
    }

    @Test
    void isCancellationException_javaCancellation() {
        assertTrue(
                ToolInvocationRunner.isCancellationException(
                        new java.util.concurrent.CancellationException("test")));
    }

    @Test
    void isCancellationException_wrappedAgentInterrupted() {
        assertTrue(
                ToolInvocationRunner.isCancellationException(
                        new RuntimeException("wrapper", new AgentInterruptedException("inner"))));
    }

    @Test
    void isCancellationException_normalException() {
        assertFalse(
                ToolInvocationRunner.isCancellationException(new RuntimeException("normal error")));
    }

    // ===== ToolContext propagation =====

    @Test
    void execute_withToolContextInReactorContext() {
        ToolHandler handler =
                new ToolHandler() {
                    @Override
                    public ToolResult execute(Map<String, Object> input) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public ToolResult execute(Map<String, Object> input, ToolContext context) {
                        assertNotNull(context);
                        return new ToolResult("ctx_tool", "with context", false, Map.of());
                    }
                };

        ToolContext toolCtx = new ToolContext(null, null, Map.of("key", "value"));

        Mono<ToolResult> execution =
                runner.execute("ctx_tool", handler, Map.of(), Duration.ofSeconds(5))
                        .contextWrite(ctx -> ctx.put(ToolInvocationRunner.CONTEXT_KEY, toolCtx));

        StepVerifier.create(execution)
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("with context", result.content());
                        })
                .verifyComplete();
    }
}
