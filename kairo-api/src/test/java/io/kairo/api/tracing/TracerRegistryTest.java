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
package io.kairo.api.tracing;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class TracerRegistryTest {

    @AfterEach
    void resetTracer() {
        TracerRegistry.register(null);
    }

    @Test
    void defaultTracerIsNoOp() {
        Tracer tracer = TracerRegistry.get();
        assertNotNull(tracer);

        Msg input = Msg.of(MsgRole.USER, "hello");
        Msg output = Msg.of(MsgRole.ASSISTANT, "hi");
        Msg result = tracer.traceAgentCall("test", input, () -> Mono.just(output)).block();
        assertSame(output, result);
    }

    @Test
    void noopTracerPassesThroughModelCall() {
        Tracer tracer = TracerRegistry.get();
        ModelResponse response =
                new ModelResponse("id", List.of(), new ModelResponse.Usage(0, 0, 0, 0), null, "m");
        ModelResponse result =
                tracer.traceModelCall("anthropic", 1, () -> Mono.just(response)).block();
        assertSame(response, result);
    }

    @Test
    void noopTracerPassesThroughToolCall() {
        Tracer tracer = TracerRegistry.get();
        ToolResult toolResult = new ToolResult("id", "ok", false, Map.of());
        ToolResult result =
                tracer.traceToolCall("bash", Map.of(), () -> Mono.just(toolResult)).block();
        assertSame(toolResult, result);
    }

    @Test
    void registerCustomTracerReplacesDefault() {
        AtomicInteger agentCalls = new AtomicInteger(0);
        AtomicInteger modelCalls = new AtomicInteger(0);
        AtomicInteger toolCalls = new AtomicInteger(0);
        AtomicInteger compactions = new AtomicInteger(0);
        AtomicInteger iterations = new AtomicInteger(0);

        Tracer custom =
                new Tracer() {
                    @Override
                    public Mono<Msg> traceAgentCall(
                            String agentName,
                            Msg input,
                            java.util.function.Supplier<Mono<Msg>> agentCall) {
                        agentCalls.incrementAndGet();
                        return agentCall.get();
                    }

                    @Override
                    public Mono<ModelResponse> traceModelCall(
                            String providerName,
                            int messageCount,
                            java.util.function.Supplier<Mono<ModelResponse>> modelCall) {
                        modelCalls.incrementAndGet();
                        return modelCall.get();
                    }

                    @Override
                    public Mono<ToolResult> traceToolCall(
                            String toolName,
                            Map<String, Object> input,
                            java.util.function.Supplier<Mono<ToolResult>> toolCall) {
                        toolCalls.incrementAndGet();
                        return toolCall.get();
                    }

                    @Override
                    public void recordCompaction(
                            int tokensSaved, float pressureBefore, float pressureAfter) {
                        compactions.incrementAndGet();
                    }

                    @Override
                    public void recordIteration(String agentName, int iteration, int tokensUsed) {
                        iterations.incrementAndGet();
                    }
                };

        TracerRegistry.register(custom);
        assertSame(custom, TracerRegistry.get());

        // Verify custom tracer is invoked
        Msg msg = Msg.of(MsgRole.USER, "test");
        TracerRegistry.get().traceAgentCall("a", msg, () -> Mono.just(msg)).block();
        assertEquals(1, agentCalls.get());

        TracerRegistry.get()
                .traceModelCall(
                        "anthropic",
                        1,
                        () ->
                                Mono.just(
                                        new ModelResponse(
                                                "id",
                                                List.of(),
                                                new ModelResponse.Usage(0, 0, 0, 0),
                                                null,
                                                "m")))
                .block();
        assertEquals(1, modelCalls.get());

        TracerRegistry.get()
                .traceToolCall(
                        "bash",
                        Map.of(),
                        () -> Mono.just(new ToolResult("id", "ok", false, Map.of())))
                .block();
        assertEquals(1, toolCalls.get());

        TracerRegistry.get().recordCompaction(1000, 0.9f, 0.5f);
        assertEquals(1, compactions.get());

        TracerRegistry.get().recordIteration("agent", 1, 500);
        assertEquals(1, iterations.get());
    }

    @Test
    void registerNullResetsToNoOp() {
        Tracer custom = new Tracer() {};
        TracerRegistry.register(custom);
        assertSame(custom, TracerRegistry.get());

        TracerRegistry.register(null);
        assertNotNull(TracerRegistry.get());
        assertNotSame(custom, TracerRegistry.get());
    }

    @Test
    void noopRecordCompactionDoesNotThrow() {
        assertDoesNotThrow(() -> TracerRegistry.get().recordCompaction(500, 0.9f, 0.6f));
    }

    @Test
    void noopRecordIterationDoesNotThrow() {
        assertDoesNotThrow(() -> TracerRegistry.get().recordIteration("agent", 5, 10000));
    }
}
