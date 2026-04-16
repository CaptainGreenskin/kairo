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
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TracerSpanTest {

    @Test
    @DisplayName("NoopTracer returns NoopSpan from all start* methods")
    void noopTracer_returnsNoopSpans() {
        NoopTracer tracer = NoopTracer.INSTANCE;
        Msg input = Msg.of(MsgRole.USER, "test");

        assertSame(NoopSpan.INSTANCE, tracer.startAgentSpan("agent", input));
        assertSame(NoopSpan.INSTANCE, tracer.startIterationSpan(NoopSpan.INSTANCE, 1));
        assertSame(NoopSpan.INSTANCE, tracer.startReasoningSpan(NoopSpan.INSTANCE, "gpt-4", 5));
        assertSame(NoopSpan.INSTANCE, tracer.startToolSpan(NoopSpan.INSTANCE, "tool", Map.of()));
    }

    @Test
    @DisplayName("NoopSpan all methods are silent no-ops")
    void noopSpan_allMethodsSilent() {
        NoopSpan span = NoopSpan.INSTANCE;

        // All these should execute without error
        assertDoesNotThrow(() -> span.setAttribute("key", "value"));
        assertDoesNotThrow(() -> span.setStatus(true, "ok"));
        assertDoesNotThrow(() -> span.setStatus(false, "error"));
        assertDoesNotThrow(span::end);

        // Verify return values
        assertEquals("", span.spanId());
        assertEquals("", span.name());
        assertNull(span.parent());
    }

    @Test
    @DisplayName("NoopSpan.INSTANCE is a singleton")
    void noopSpan_singletonInstance() {
        assertSame(NoopSpan.INSTANCE, NoopSpan.INSTANCE);
        // Verify it's the same object identity
        Span span1 = NoopSpan.INSTANCE;
        Span span2 = NoopSpan.INSTANCE;
        assertSame(span1, span2);
    }
}
