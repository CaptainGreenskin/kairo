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
package io.kairo.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.tracing.Span;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Full trace tree integration test simulating a complete agent call flow. */
class OTelTracerIntegrationTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private OTelTracer tracer;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider =
                SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build();
        tracer = new OTelTracer(tracerProvider.get("kairo-integration-test"));
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void fullAgentCallFlowProducesCorrectTraceTree() {
        Msg input = Msg.of(MsgRole.USER, "What is 2+2?");

        // 1. Start agent span
        Span agentSpan = tracer.startAgentSpan("math-agent", input);
        tracer.recordTokenUsage(agentSpan, 10, 0, 5, 0);

        // 2. Start iteration
        Span iterationSpan = tracer.startIterationSpan(agentSpan, 0);

        // 3. Reasoning call
        Span reasoningSpan = tracer.startReasoningSpan(iterationSpan, "gpt-4o", 3);
        tracer.recordTokenUsage(reasoningSpan, 100, 50, 20, 10);
        reasoningSpan.end();

        // 4. Tool call
        Span toolSpan = tracer.startToolSpan(iterationSpan, "calculator", Map.of("expr", "2+2"));
        tracer.recordToolResult(toolSpan, "calculator", true, Duration.ofMillis(42));
        toolSpan.end();

        // 5. End iteration and agent
        iterationSpan.end();
        agentSpan.setStatus(true, "completed");
        agentSpan.end();

        // --- Verify exported spans ---
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(4, spans.size(), "Expected exactly 4 spans");

        SpanData agentData = findSpan(spans, "agent:math-agent");
        SpanData iterationData = findSpan(spans, "iteration-0");
        SpanData reasoningData = findSpan(spans, "reasoning:gpt-4o");
        SpanData toolData = findSpan(spans, "tool:calculator");

        // Verify names
        assertEquals("agent:math-agent", agentData.getName());
        assertEquals("iteration-0", iterationData.getName());
        assertEquals("reasoning:gpt-4o", reasoningData.getName());
        assertEquals("tool:calculator", toolData.getName());

        // Verify parent-child relationships
        String traceId = agentData.getTraceId();
        assertEquals(traceId, iterationData.getTraceId());
        assertEquals(traceId, reasoningData.getTraceId());
        assertEquals(traceId, toolData.getTraceId());

        assertTrue(
                agentData.getParentSpanId().equals("0000000000000000")
                        || agentData.getParentSpanId().isEmpty());
        assertEquals(agentData.getSpanId(), iterationData.getParentSpanId());
        assertEquals(iterationData.getSpanId(), reasoningData.getParentSpanId());
        assertEquals(iterationData.getSpanId(), toolData.getParentSpanId());

        // Verify agent span attributes (Kairo "agent.name" → GenAI semconv "gen_ai.agent.name").
        assertEquals(
                "math-agent",
                agentData.getAttributes().get(AttributeKey.stringKey("gen_ai.agent.name")));
        assertEquals(
                10L,
                agentData.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
        assertEquals(StatusCode.OK, agentData.getStatus().getStatusCode());

        // Verify reasoning span attributes
        assertEquals(
                3L,
                reasoningData
                        .getAttributes()
                        .get(AttributeKey.longKey("gen_ai.request.message_count")));
        assertEquals(
                100L,
                reasoningData
                        .getAttributes()
                        .get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
        assertEquals(
                50L,
                reasoningData
                        .getAttributes()
                        .get(AttributeKey.longKey("gen_ai.usage.output_tokens")));
        assertEquals(
                20L,
                reasoningData
                        .getAttributes()
                        .get(AttributeKey.longKey("gen_ai.usage.cache_read_tokens")));
        assertEquals(
                10L,
                reasoningData
                        .getAttributes()
                        .get(AttributeKey.longKey("gen_ai.usage.cache_creation_tokens")));

        // Verify tool span attributes
        assertEquals(
                "calculator",
                toolData.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.name")));
        assertEquals(
                true, toolData.getAttributes().get(AttributeKey.booleanKey("gen_ai.tool.success")));
        assertEquals(
                42L, toolData.getAttributes().get(AttributeKey.longKey("gen_ai.tool.duration_ms")));
    }

    @Test
    void multipleIterationsWithCompaction() {
        Msg input = Msg.of(MsgRole.USER, "complex query");

        Span agentSpan = tracer.startAgentSpan("complex-agent", input);

        // Iteration 0
        Span iter0 = tracer.startIterationSpan(agentSpan, 0);
        Span reasoning0 = tracer.startReasoningSpan(iter0, "claude-3", 5);
        reasoning0.end();
        iter0.end();

        // Record compaction between iterations
        tracer.recordCompaction(agentSpan, "summarize", 3000);

        // Iteration 1
        Span iter1 = tracer.startIterationSpan(agentSpan, 1);
        Span reasoning1 = tracer.startReasoningSpan(iter1, "claude-3", 3);
        reasoning1.end();
        iter1.end();

        agentSpan.end();

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(5, spans.size());

        // Verify compaction attributes on agent span
        SpanData agentData = findSpan(spans, "agent:complex-agent");
        assertEquals(
                "summarize",
                agentData
                        .getAttributes()
                        .get(AttributeKey.stringKey("gen_ai.compaction.strategy")));
        assertEquals(
                3000L,
                agentData
                        .getAttributes()
                        .get(AttributeKey.longKey("gen_ai.compaction.tokens_saved")));
    }

    @Test
    void exceptionRecordingInTraceFlow() {
        Msg input = Msg.of(MsgRole.USER, "fail test");

        Span agentSpan = tracer.startAgentSpan("error-agent", input);
        Span iterationSpan = tracer.startIterationSpan(agentSpan, 0);
        Span toolSpan = tracer.startToolSpan(iterationSpan, "failing-tool", Map.of());

        RuntimeException error = new RuntimeException("tool execution failed");
        tracer.recordException(toolSpan, error);
        toolSpan.end();
        iterationSpan.end();

        tracer.recordException(agentSpan, error);
        agentSpan.end();

        SpanData toolData = findSpan(exporter.getFinishedSpanItems(), "tool:failing-tool");
        assertEquals(
                "java.lang.RuntimeException",
                toolData.getAttributes().get(AttributeKey.stringKey("exception.type")));
        assertEquals(
                "tool execution failed",
                toolData.getAttributes().get(AttributeKey.stringKey("exception.message")));
        assertEquals(StatusCode.ERROR, toolData.getStatus().getStatusCode());
    }

    private SpanData findSpan(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found: " + name));
    }
}
