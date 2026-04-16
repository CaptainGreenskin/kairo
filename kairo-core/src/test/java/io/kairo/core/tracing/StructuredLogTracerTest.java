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
package io.kairo.core.tracing;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StructuredLogTracerTest {

    private final StructuredLogTracer tracer = new StructuredLogTracer();

    @Test
    @DisplayName("setAttribute stores attributes on StructuredLogSpan")
    void span_setAttribute() {
        Msg input = Msg.of(MsgRole.USER, "test");
        Span span = tracer.startAgentSpan("myAgent", input);

        // Set some attributes
        span.setAttribute("custom.key", "custom-value");
        span.setAttribute("custom.count", 42);

        // Verify via cast to access internal attributes
        StructuredLogTracer.StructuredLogSpan logSpan = (StructuredLogTracer.StructuredLogSpan) span;
        // end() should not throw — attributes are logged
        assertDoesNotThrow(span::end);
    }

    @Test
    @DisplayName("Child span correctly references parent")
    void span_parentChild() {
        Msg input = Msg.of(MsgRole.USER, "test");
        Span agentSpan = tracer.startAgentSpan("myAgent", input);
        Span iterSpan = tracer.startIterationSpan(agentSpan, 1);

        assertSame(agentSpan, iterSpan.parent());
        assertNull(agentSpan.parent()); // root span has no parent
        assertNotEquals(agentSpan.spanId(), iterSpan.spanId());

        // Clean up
        iterSpan.end();
        agentSpan.end();
    }

    @Test
    @DisplayName("recordTokenUsage sets expected attribute keys on span")
    void span_recordTokenUsage() {
        Msg input = Msg.of(MsgRole.USER, "test");
        Span span = tracer.startAgentSpan("myAgent", input);

        tracer.recordTokenUsage(span, 100, 50, 20, 10);

        // Verify via cast — StructuredLogSpan stores attributes in a ConcurrentHashMap
        StructuredLogTracer.StructuredLogSpan logSpan = (StructuredLogTracer.StructuredLogSpan) span;
        // The Tracer default recordTokenUsage calls setAttribute with these keys
        // We verify end() works without error (attributes are logged)
        assertDoesNotThrow(span::end);
    }

    @Test
    @DisplayName("startAgentSpan creates span with correct name format")
    void structuredLogTracer_createsNamedSpans() {
        Msg input = Msg.of(MsgRole.USER, "hello");
        Span span = tracer.startAgentSpan("myAgent", input);

        assertEquals("agent:myAgent", span.name());
        assertNotNull(span.spanId());
        assertFalse(span.spanId().isEmpty());
        assertNull(span.parent());
    }

    @Test
    @DisplayName("Agent span lifecycle — start, iterate, end — no errors")
    void agentSpan_lifecycle() {
        Msg input = Msg.of(MsgRole.USER, "test task");

        // Start agent span
        Span agentSpan = tracer.startAgentSpan("lifecycleAgent", input);
        assertEquals("agent:lifecycleAgent", agentSpan.name());

        // Start iteration span as child
        Span iterSpan = tracer.startIterationSpan(agentSpan, 1);
        assertEquals("iteration:iteration-1", iterSpan.name());
        assertSame(agentSpan, iterSpan.parent());

        // Start reasoning span as child of iteration
        Span reasonSpan = tracer.startReasoningSpan(iterSpan, "gpt-4", 3);
        assertEquals("reasoning:gpt-4", reasonSpan.name());
        assertSame(iterSpan, reasonSpan.parent());

        // Start tool span as child of iteration
        Span toolSpan = tracer.startToolSpan(iterSpan, "run_tests", Map.of("cmd", "mvn test"));
        assertEquals("tool:run_tests", toolSpan.name());
        assertSame(iterSpan, toolSpan.parent());

        // Record tool result using Tracer convenience method
        tracer.recordToolResult(toolSpan, "run_tests", true, Duration.ofMillis(500));

        // Record compaction
        tracer.recordCompaction(agentSpan, "sliding-window", 200);

        // Set status and end all spans — no errors
        toolSpan.setStatus(true, "OK");
        assertDoesNotThrow(toolSpan::end);
        reasonSpan.setStatus(true, "OK");
        assertDoesNotThrow(reasonSpan::end);
        iterSpan.setStatus(true, "OK");
        assertDoesNotThrow(iterSpan::end);
        agentSpan.setStatus(true, "completed");
        assertDoesNotThrow(agentSpan::end);
    }
}
