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

class OTelTracerTest {

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
        tracer = new OTelTracer(tracerProvider.get("test"));
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    private Msg testInput() {
        return Msg.of(MsgRole.USER, "test input");
    }

    // --- startAgentSpan ---

    @Test
    void startAgentSpanCreatesRootSpanWithAgentName() {
        Span span = tracer.startAgentSpan("my-agent", testInput());
        assertNotNull(span);
        assertEquals("agent:my-agent", span.name());
        assertNull(span.parent());
        span.end();

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        SpanData data = spans.get(0);
        assertEquals("agent:my-agent", data.getName());
        // Kairo short key "agent.name" maps to the GenAI semconv key "gen_ai.agent.name".
        assertEquals(
                "my-agent", data.getAttributes().get(AttributeKey.stringKey("gen_ai.agent.name")));
    }

    // --- startIterationSpan ---

    @Test
    void startIterationSpanCreatesChildWithIterationNumber() {
        Span agent = tracer.startAgentSpan("agent", testInput());
        Span iteration = tracer.startIterationSpan(agent, 3);

        assertNotNull(iteration);
        assertEquals("iteration-3", iteration.name());
        assertEquals(agent, iteration.parent());

        iteration.end();
        agent.end();

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(2, spans.size());

        SpanData iterationData = spans.get(0); // iteration ends first
        SpanData agentData = spans.get(1);

        assertEquals(agentData.getSpanId(), iterationData.getParentSpanId());
        // Verify agent.iteration attribute is set and mapped to GenAI semconv
        assertEquals(
                3L,
                iterationData.getAttributes().get(AttributeKey.longKey("gen_ai.agent.iteration")));
    }

    // --- startReasoningSpan ---

    @Test
    void startReasoningSpanCreatesChildWithModelAndMessageCount() {
        Span agent = tracer.startAgentSpan("agent", testInput());
        Span iteration = tracer.startIterationSpan(agent, 0);
        Span reasoning = tracer.startReasoningSpan(iteration, "gpt-4", 5);

        assertNotNull(reasoning);
        assertEquals("reasoning:gpt-4", reasoning.name());
        assertEquals(iteration, reasoning.parent());

        reasoning.end();
        iteration.end();
        agent.end();

        SpanData reasoningData = exporter.getFinishedSpanItems().get(0);
        assertEquals(
                5L,
                reasoningData
                        .getAttributes()
                        .get(AttributeKey.longKey("gen_ai.request.message_count")));
    }

    // --- startToolSpan ---

    @Test
    void startToolSpanCreatesChildWithToolName() {
        Span agent = tracer.startAgentSpan("agent", testInput());
        Span iteration = tracer.startIterationSpan(agent, 0);
        Span tool = tracer.startToolSpan(iteration, "calculator", Map.of("expr", "1+1"));

        assertNotNull(tool);
        assertEquals("tool:calculator", tool.name());
        assertEquals(iteration, tool.parent());

        tool.end();
        iteration.end();
        agent.end();
    }

    // --- Parent-child hierarchy ---

    @Test
    void parentChildHierarchyIsCorrect() {
        Span agent = tracer.startAgentSpan("agent", testInput());
        Span iteration = tracer.startIterationSpan(agent, 0);
        Span reasoning = tracer.startReasoningSpan(iteration, "gpt-4", 3);
        Span tool = tracer.startToolSpan(iteration, "search", Map.of());

        // End in reverse order
        tool.end();
        reasoning.end();
        iteration.end();
        agent.end();

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(4, spans.size());

        // Find spans by name
        SpanData agentData = findSpan(spans, "agent:agent");
        SpanData iterationData = findSpan(spans, "iteration-0");
        SpanData reasoningData = findSpan(spans, "reasoning:gpt-4");
        SpanData toolData = findSpan(spans, "tool:search");

        // Verify hierarchy
        // Agent is root (no parent or parent is 0000...)
        assertTrue(
                agentData.getParentSpanId().equals("0000000000000000")
                        || agentData.getParentSpanId().isEmpty());

        // Iteration is child of agent
        assertEquals(agentData.getSpanId(), iterationData.getParentSpanId());

        // Reasoning and tool are children of iteration
        assertEquals(iterationData.getSpanId(), reasoningData.getParentSpanId());
        assertEquals(iterationData.getSpanId(), toolData.getParentSpanId());

        // All in same trace
        String traceId = agentData.getTraceId();
        assertEquals(traceId, iterationData.getTraceId());
        assertEquals(traceId, reasoningData.getTraceId());
        assertEquals(traceId, toolData.getTraceId());
    }

    // --- recordTokenUsage ---

    @Test
    void recordTokenUsageSetsAllAttributes() {
        Span span = tracer.startAgentSpan("agent", testInput());
        tracer.recordTokenUsage(span, 100, 200, 50, 30);
        span.end();

        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals(
                100L, data.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
        assertEquals(
                200L, data.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));
        assertEquals(
                50L,
                data.getAttributes().get(AttributeKey.longKey("gen_ai.usage.cache_read_tokens")));
        assertEquals(
                30L,
                data.getAttributes()
                        .get(AttributeKey.longKey("gen_ai.usage.cache_creation_tokens")));
    }

    // --- recordToolResult ---

    @Test
    void recordToolResultSetsAllAttributes() {
        Span span = tracer.startAgentSpan("agent", testInput());
        tracer.recordToolResult(span, "calculator", true, Duration.ofMillis(1500));
        span.end();

        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals(
                "calculator", data.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.name")));
        assertEquals(
                true, data.getAttributes().get(AttributeKey.booleanKey("gen_ai.tool.success")));
        assertEquals(
                1500L, data.getAttributes().get(AttributeKey.longKey("gen_ai.tool.duration_ms")));
    }

    // --- recordCompaction ---

    @Test
    void recordCompactionSetsAttributes() {
        Span span = tracer.startAgentSpan("agent", testInput());
        tracer.recordCompaction(span, "summarize", 5000);
        span.end();

        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals(
                "summarize",
                data.getAttributes().get(AttributeKey.stringKey("gen_ai.compaction.strategy")));
        assertEquals(
                5000L,
                data.getAttributes().get(AttributeKey.longKey("gen_ai.compaction.tokens_saved")));
    }

    // --- recordException ---

    @Test
    void recordExceptionSetsAttributesAndStatus() {
        Span span = tracer.startAgentSpan("agent", testInput());
        RuntimeException ex = new RuntimeException("test error");
        tracer.recordException(span, ex);
        span.end();

        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals(
                "java.lang.RuntimeException",
                data.getAttributes().get(AttributeKey.stringKey("exception.type")));
        assertEquals(
                "test error",
                data.getAttributes().get(AttributeKey.stringKey("exception.message")));
        assertEquals(StatusCode.ERROR, data.getStatus().getStatusCode());
    }

    @Test
    void recordExceptionTruncatesLongMessage() {
        String longMsg = "x".repeat(2000);
        Span span = tracer.startAgentSpan("agent", testInput());
        tracer.recordException(span, new RuntimeException(longMsg));
        span.end();

        SpanData data = exporter.getFinishedSpanItems().get(0);
        String recorded = data.getAttributes().get(AttributeKey.stringKey("exception.message"));
        assertNotNull(recorded);
        assertTrue(
                recorded.length() <= OTelTracer.MAX_EXCEPTION_MESSAGE_LENGTH + 20,
                "Message should be truncated to safe max length");
        assertTrue(recorded.endsWith("...[truncated]"));
    }

    @Test
    void recordExceptionStripsEmailPII() {
        Span span = tracer.startAgentSpan("agent", testInput());
        tracer.recordException(span, new RuntimeException("Error for user@example.com"));
        span.end();

        SpanData data = exporter.getFinishedSpanItems().get(0);
        String recorded = data.getAttributes().get(AttributeKey.stringKey("exception.message"));
        assertFalse(recorded.contains("user@example.com"), "Email should be redacted");
        assertTrue(recorded.contains("[REDACTED_EMAIL]"));
    }

    @Test
    void recordExceptionStripsApiKeyLikeSecrets() {
        Span span = tracer.startAgentSpan("agent", testInput());
        tracer.recordException(span, new RuntimeException("Failed with api_key=sk-12345abcde"));
        span.end();

        SpanData data = exporter.getFinishedSpanItems().get(0);
        String recorded = data.getAttributes().get(AttributeKey.stringKey("exception.message"));
        assertFalse(recorded.contains("sk-12345abcde"), "API key should be redacted");
        assertTrue(recorded.contains("[REDACTED_SECRET]"));
    }

    @Test
    void recordExceptionWithNullMessage() {
        Span span = tracer.startAgentSpan("agent", testInput());
        tracer.recordException(span, new RuntimeException((String) null));
        span.end();

        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals("", data.getAttributes().get(AttributeKey.stringKey("exception.message")));
        assertEquals(StatusCode.ERROR, data.getStatus().getStatusCode());
    }

    // --- additionalAllowedKeys ---

    @Test
    void tracerWithAdditionalAllowedKeysAcceptsCustomAttributes() {
        OTelTracer customTracer =
                new OTelTracer(tracerProvider.get("custom"), java.util.Set.of("my.custom"));
        Span span = customTracer.startAgentSpan("agent", testInput());
        span.setAttribute("my.custom", "hello");
        span.end();

        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals("hello", data.getAttributes().get(AttributeKey.stringKey("my.custom")));
    }

    // --- Null safety ---

    @Test
    void constructorRejectsNullTracer() {
        assertThrows(NullPointerException.class, () -> new OTelTracer(null));
    }

    private SpanData findSpan(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found: " + name));
    }
}
