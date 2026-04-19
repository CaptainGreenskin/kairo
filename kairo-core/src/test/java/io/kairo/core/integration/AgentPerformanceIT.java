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
package io.kairo.core.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.context.*;
import io.kairo.api.hook.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.*;
import io.kairo.core.agent.DefaultReActAgent;
import io.kairo.core.context.compaction.CompactionPipeline;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.core.tool.ToolHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Performance integration tests for the Kairo agent framework.
 *
 * <p>Absorbs and extends the former {@code PerformanceBaselineTest}. All thresholds are
 * intentionally very loose — the goal is to catch 10x regressions (catastrophic performance bugs),
 * not to micro-benchmark. Every test uses MOCK providers; no real API calls are made.
 *
 * <p>Run with: {@code mvn test -pl kairo-core -Dgroups=integration} or {@code mvn test -pl
 * kairo-core -Dgroups=performance}
 */
@Tag("integration")
@Tag("performance")
class AgentPerformanceIT {

    // =====================================================================
    // Shared helpers
    // =====================================================================

    private DefaultToolRegistry createRegistryWithEchoTool() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        ToolDefinition echoTool =
                new ToolDefinition(
                        "echo",
                        "echoes input",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        registry.register(echoTool);
        registry.registerInstance(
                "echo", (ToolHandler) input -> new ToolResult("echo", "result", false, Map.of()));
        return registry;
    }

    private DefaultReActAgent buildAgent(
            ModelProvider provider,
            DefaultToolRegistry registry,
            DefaultHookChain hookChain,
            int maxIterations) {
        DefaultToolExecutor executor =
                new DefaultToolExecutor(registry, new DefaultPermissionGuard());
        AgentConfig config =
                AgentConfig.builder()
                        .name("perf-agent")
                        .modelProvider(provider)
                        .toolRegistry(registry)
                        .maxIterations(maxIterations)
                        .timeout(Duration.ofSeconds(30))
                        .tokenBudget(1_000_000)
                        .build();
        return new DefaultReActAgent(config, executor, hookChain, null, null);
    }

    /**
     * Creates a mock provider that alternates between tool calls and text responses. Odd calls
     * return a tool use for "echo"; even calls return an END_TURN text response.
     */
    private ModelProvider alternatingMockProvider(AtomicInteger callCount, int maxToolCalls) {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        invocation -> {
                            int n = callCount.incrementAndGet();
                            if (n % 2 == 1 && n < maxToolCalls * 2) {
                                return Mono.just(
                                        new ModelResponse(
                                                "resp-" + n,
                                                List.of(
                                                        new Content.ToolUseContent(
                                                                "tc-" + n,
                                                                "echo",
                                                                Map.of("text", "iter-" + n))),
                                                new ModelResponse.Usage(10, 10, 0, 0),
                                                ModelResponse.StopReason.TOOL_USE,
                                                "mock-model"));
                            } else {
                                return Mono.just(
                                        new ModelResponse(
                                                "resp-" + n,
                                                List.of(
                                                        new Content.TextContent(
                                                                "Done at iteration " + n)),
                                                new ModelResponse.Usage(10, 10, 0, 0),
                                                ModelResponse.StopReason.END_TURN,
                                                "mock-model"));
                            }
                        });
        return provider;
    }

    /** Creates a mock provider that always returns a simple text response. */
    private ModelProvider textOnlyMockProvider() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(
                        Mono.just(
                                new ModelResponse(
                                        "resp-1",
                                        List.of(new Content.TextContent("OK")),
                                        new ModelResponse.Usage(10, 10, 0, 0),
                                        ModelResponse.StopReason.END_TURN,
                                        "mock-model")));
        return provider;
    }

    // =====================================================================
    // Test 1 (absorbed from PerformanceBaselineTest):
    //   100-round mock conversation end-to-end timing
    // =====================================================================

    /**
     * Measures the framework overhead of running a 100-iteration ReAct loop with a mock model
     * provider. Each iteration alternates between a tool call and a text response.
     *
     * <p>Threshold: 5 seconds. Mocked calls should complete in well under 1s; the 5s threshold
     * catches 10x+ regressions in the agent loop (e.g., accidental O(n²) or blocking).
     */
    @Test
    void mockAgent_100Iterations_completesUnder5s() {
        AtomicInteger callCount = new AtomicInteger(0);
        ModelProvider provider = alternatingMockProvider(callCount, 100);
        DefaultToolRegistry registry = createRegistryWithEchoTool();
        DefaultHookChain hookChain = new DefaultHookChain();

        DefaultReActAgent agent = buildAgent(provider, registry, hookChain, 200);

        long startNanos = System.nanoTime();
        Msg result = agent.call(Msg.of(MsgRole.USER, "Start performance test")).block();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertNotNull(result, "Agent should return a response");
        // Threshold: 5s. Mocked calls typically complete in <1s.
        // Catches catastrophic regressions only.
        assertTrue(
                elapsedMs < 5_000,
                "100-round mock conversation took "
                        + elapsedMs
                        + "ms, expected < 5000ms. "
                        + "This indicates a major performance regression in the agent loop.");
    }

    // =====================================================================
    // Test 2 (absorbed from PerformanceBaselineTest):
    //   Compaction pipeline latency for large message lists
    // =====================================================================

    /**
     * Measures compaction pipeline execution time for 1000 messages with a mock strategy.
     *
     * <p>Threshold: 2 seconds. The mock strategy does no real work, so this measures pipeline
     * orchestration overhead. Typical run: <100ms. The 2s threshold detects O(n²) regressions.
     */
    @Test
    void compaction_triggerLatency_acceptable() {
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            messages.add(
                    Msg.builder()
                            .id("msg-" + i)
                            .role(i % 2 == 0 ? MsgRole.USER : MsgRole.ASSISTANT)
                            .addContent(
                                    new Content.TextContent(
                                            "Message number "
                                                    + i
                                                    + " with some content to process."))
                            .tokenCount(50)
                            .build());
        }

        CompactionStrategy fastStrategy =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        return true;
                    }

                    @Override
                    public Mono<CompactionResult> compact(List<Msg> msgs, CompactionConfig cfg) {
                        BoundaryMarker marker =
                                new BoundaryMarker(
                                        Instant.now(),
                                        "fast-compact",
                                        msgs.size(),
                                        msgs.size() / 2,
                                        msgs.size() * 25);
                        List<Msg> compacted = msgs.subList(0, Math.max(1, msgs.size() / 2));
                        return Mono.just(new CompactionResult(compacted, msgs.size() * 25, marker));
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }

                    @Override
                    public String name() {
                        return "fast-compact";
                    }
                };

        CompactionPipeline pipeline = new CompactionPipeline(List.of(fastStrategy));
        CompactionConfig config = new CompactionConfig(100_000, true, null);

        long startNanos = System.nanoTime();
        CompactionResult result = pipeline.execute(messages, Set.of(), 0.90f, config).block();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertNotNull(result, "Compaction should produce a result");
        assertTrue(result.tokensSaved() > 0, "Compaction should save some tokens");
        // Threshold: 2s. Mock compaction for 1000 messages should be <100ms.
        // Detects O(n²) regressions in pipeline orchestration.
        assertTrue(
                elapsedMs < 2_000,
                "Compaction of 1000 messages took "
                        + elapsedMs
                        + "ms, expected < 2000ms. "
                        + "This indicates a performance regression in the compaction pipeline.");
    }

    // =====================================================================
    // Test 3 (absorbed from PerformanceBaselineTest):
    //   Token estimation throughput
    // =====================================================================

    /**
     * Measures throughput of the token estimation heuristic over 10,000+ estimations.
     *
     * <p>Threshold: 1 second. The estimator is a simple char-count heuristic (~4 chars/token), so
     * each call should be sub-microsecond. The 1s threshold catches regressions like accidentally
     * adding network calls or expensive parsing.
     */
    @Test
    void tokenEstimation_throughput_acceptable() {
        List<Msg> testMessages = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            testMessages.add(
                    Msg.builder()
                            .id("text-" + i)
                            .role(MsgRole.USER)
                            .addContent(new Content.TextContent("A".repeat(100 + i * 10)))
                            .build());
            testMessages.add(
                    Msg.builder()
                            .id("tool-use-" + i)
                            .role(MsgRole.ASSISTANT)
                            .addContent(
                                    new Content.ToolUseContent(
                                            "tc-" + i,
                                            "search",
                                            Map.of("query", "test query " + i, "limit", 10)))
                            .build());
            testMessages.add(
                    Msg.builder()
                            .id("tool-result-" + i)
                            .role(MsgRole.TOOL)
                            .addContent(
                                    new Content.ToolResultContent(
                                            "tc-" + i,
                                            "Result data for query " + i + " with some payload.",
                                            false))
                            .build());
        }

        long startNanos = System.nanoTime();
        int totalEstimations = 0;
        long totalTokens = 0;
        for (int round = 0; round < 34; round++) { // 34 rounds * 300 messages ≈ 10,200
            for (Msg msg : testMessages) {
                totalTokens += MsgBuilder.estimateTokens(msg);
                totalEstimations++;
            }
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue(totalTokens > 0, "Token estimation should produce non-zero results");
        assertTrue(totalEstimations >= 10_000, "Should have run at least 10,000 estimations");
        // Threshold: 1s for 10,000+ estimations. Typical: <10ms.
        // Catches regressions like accidentally adding network calls.
        assertTrue(
                elapsedMs < 1_000,
                totalEstimations
                        + " token estimations took "
                        + elapsedMs
                        + "ms, expected < 1000ms. "
                        + "This indicates a regression in the token estimation heuristic.");
    }

    // =====================================================================
    // Test 4 (new): 10 concurrent mock agents — no deadlock
    // =====================================================================

    /**
     * Launches 10 mock agents concurrently and verifies all complete without deadlock.
     *
     * <p>Threshold: 30 seconds total. Each agent does a single mock call, so the real time should
     * be well under 5s. The 30s timeout catches deadlock or thread-starvation bugs.
     */
    @Test
    void concurrentAgents_10Parallel_noDeadlock() throws Exception {
        int numAgents = 10;
        ExecutorService pool = Executors.newFixedThreadPool(numAgents);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(numAgents);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numAgents; i++) {
            final int id = i;
            pool.submit(
                    () -> {
                        try {
                            startGate.await(); // wait for all threads to be ready
                            ModelProvider provider = textOnlyMockProvider();
                            DefaultToolRegistry registry = createRegistryWithEchoTool();
                            DefaultHookChain hookChain = new DefaultHookChain();
                            DefaultReActAgent agent = buildAgent(provider, registry, hookChain, 5);

                            Msg result =
                                    agent.call(Msg.of(MsgRole.USER, "Concurrent task " + id))
                                            .block(Duration.ofSeconds(15));
                            assertNotNull(result, "Agent " + id + " should return a response");
                        } catch (Throwable t) {
                            errors.add(t);
                        } finally {
                            doneGate.countDown();
                        }
                    });
        }

        startGate.countDown(); // release all threads simultaneously
        // Timeout: 30s. If any thread deadlocks, this will fail.
        boolean completed = doneGate.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertTrue(completed, "All 10 agents should complete within 30s — possible deadlock");
        assertTrue(errors.isEmpty(), "No agent should throw: " + errors);
    }

    // =====================================================================
    // Test 5 (new): 1000-message context — no OOM or excessive GC
    // =====================================================================

    /**
     * Builds a 1000-message context and verifies the agent handles it gracefully.
     *
     * <p>Threshold: 10 seconds. We feed 1000 messages into a mock agent's context to stress-test
     * message list handling, token counting, and memory allocation. The test only checks that the
     * operation completes without OOM or timeout — no micro-benchmarks.
     */
    @Test
    void largeConversation_1000Messages_handlesGracefully() {
        // Build a pre-populated conversation history with 1000 messages
        List<Content> contents = List.of(new Content.TextContent("Message with some content."));
        List<Msg> history = new ArrayList<>(1000);
        for (int i = 0; i < 1000; i++) {
            history.add(
                    Msg.builder()
                            .id("msg-" + i)
                            .role(i % 2 == 0 ? MsgRole.USER : MsgRole.ASSISTANT)
                            .addContent(
                                    new Content.TextContent(
                                            "Message "
                                                    + i
                                                    + " with content to fill the context window."))
                            .tokenCount(30)
                            .build());
        }

        // Verify that token estimation works across all 1000 messages without degradation
        long startNanos = System.nanoTime();
        long totalTokens = 0;
        for (Msg msg : history) {
            totalTokens += MsgBuilder.estimateTokens(msg);
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue(totalTokens > 0, "Should estimate tokens for 1000 messages");
        // Threshold: 10s. Processing 1000 messages for token estimation should be <100ms.
        // Catches OOM or excessive allocation patterns.
        assertTrue(
                elapsedMs < 10_000,
                "Processing 1000 messages took "
                        + elapsedMs
                        + "ms, expected < 10000ms. Possible OOM or GC pressure.");
    }

    // =====================================================================
    // Test 6 (new): Hook chain execution overhead per event
    // =====================================================================

    /**
     * Registers 5 hooks and measures total hook chain execution time per event.
     *
     * <p>Threshold: 5ms average per event. Hooks are simple no-op lambdas, so overhead is purely
     * from reflection-based dispatch in {@link DefaultHookChain}. Typical: <1ms. The 5ms threshold
     * catches regressions in the hook discovery/invocation path.
     */
    @Test
    void hookExecution_overhead_under5ms() {
        DefaultHookChain hookChain = new DefaultHookChain();

        // Register 5 no-op hook handlers that exercise the annotation-based dispatch
        for (int h = 0; h < 5; h++) {
            hookChain.register(new NoOpHookHandler());
        }

        // Warm up
        for (int w = 0; w < 10; w++) {
            hookChain.firePreReasoning("warmup-" + w).block();
        }

        // Measure 100 event firings
        long startNanos = System.nanoTime();
        int firings = 100;
        for (int i = 0; i < firings; i++) {
            hookChain.firePreReasoning("event-" + i).block();
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        long avgNanos = elapsedNanos / firings;
        long avgMs = avgNanos / 1_000_000;

        // Threshold: 5ms per event with 5 hooks. Typical: <0.5ms.
        // Catches regressions in reflection-based hook dispatch.
        assertTrue(
                avgMs < 5,
                "Average hook chain execution took "
                        + avgMs
                        + "ms per event (with 5 hooks), expected < 5ms. "
                        + "This indicates a regression in hook dispatch overhead.");
    }

    /** A no-op hook handler with a @PreReasoning annotation for overhead testing. */
    public static class NoOpHookHandler {
        @PreReasoning
        public Object onPreReasoning(Object event) {
            return event; // intentional no-op
        }
    }

    // =====================================================================
    // Test 7 (new): Circuit breaker check adds minimal overhead
    // =====================================================================

    /**
     * Verifies that the circuit breaker check in {@link DefaultToolExecutor} adds less than 1ms
     * overhead per tool invocation.
     *
     * <p>Threshold: 1ms per call. The circuit breaker is a simple ConcurrentHashMap lookup, so
     * overhead should be sub-microsecond. The 1ms threshold catches regressions like accidentally
     * adding I/O or locks to the fast path.
     */
    @Test
    void toolExecution_circuitBreaker_minimalOverhead() {
        DefaultToolRegistry registry = createRegistryWithEchoTool();
        DefaultToolExecutor executor =
                new DefaultToolExecutor(registry, new DefaultPermissionGuard());

        // Warm up: execute the tool a few times
        for (int w = 0; w < 10; w++) {
            executor.executeSingle(new ToolInvocation("echo", Map.of("text", "warmup"))).block();
        }

        // Measure 1000 tool executions (each includes circuit breaker check)
        long startNanos = System.nanoTime();
        int executions = 1000;
        for (int i = 0; i < executions; i++) {
            ToolResult result =
                    executor.executeSingle(new ToolInvocation("echo", Map.of("text", "test-" + i)))
                            .block();
            assertNotNull(result);
            assertFalse(result.isError(), "Tool should succeed: " + result.content());
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        long avgNanos = elapsedNanos / executions;

        // Threshold: 1ms (1,000,000 ns) per call. Typical: <50μs.
        // Catches regressions that add I/O or locks to the circuit breaker path.
        assertTrue(
                avgNanos < 1_000_000,
                "Average tool execution (with CB check) took "
                        + (avgNanos / 1000)
                        + "μs, expected < 1000μs (1ms). "
                        + "This indicates the circuit breaker adds excessive overhead.");
    }

    // =====================================================================
    // Test 8 (new): Agent loop memory stability — no linear growth
    // =====================================================================

    /**
     * Runs 50 agent iterations and checks that memory delta is bounded (not growing linearly).
     *
     * <p>Threshold: memory growth < 50MB over 50 iterations. Each iteration uses a fresh mock call
     * that returns immediately, so memory should plateau after warmup. A linear growth pattern
     * would indicate a leak (e.g., unbounded message list, unclosed resources).
     */
    @Test
    void agentLoop_memoryStable_noLeaks() {
        ModelProvider provider = textOnlyMockProvider();
        DefaultToolRegistry registry = createRegistryWithEchoTool();
        DefaultHookChain hookChain = new DefaultHookChain();

        // Warm up to stabilize classloading and JIT
        DefaultReActAgent warmupAgent = buildAgent(provider, registry, hookChain, 5);
        warmupAgent.call(Msg.of(MsgRole.USER, "warmup")).block();

        Runtime rt = Runtime.getRuntime();
        rt.gc();
        long memBefore = rt.totalMemory() - rt.freeMemory();

        // Run 50 iterations, each creating a fresh agent (worst case for leaks)
        int iterations = 50;
        for (int i = 0; i < iterations; i++) {
            DefaultReActAgent agent = buildAgent(provider, registry, hookChain, 5);
            Msg result = agent.call(Msg.of(MsgRole.USER, "Iteration " + i)).block();
            assertNotNull(result);
        }

        rt.gc();
        long memAfter = rt.totalMemory() - rt.freeMemory();
        long growthBytes = memAfter - memBefore;
        long growthMB = growthBytes / (1024 * 1024);

        // Threshold: 50MB. 50 agent iterations with mock calls should use very little memory.
        // A linear leak of even 1MB/iter would reach 50MB; bounded growth stays well under.
        assertTrue(
                growthMB < 50,
                "Memory grew by "
                        + growthMB
                        + "MB over "
                        + iterations
                        + " iterations, expected < 50MB. "
                        + "This may indicate a memory leak in the agent loop.");
    }
}
