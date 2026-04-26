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
package io.kairo.core.guardrail;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.guardrail.SecurityEventSink;
import io.kairo.api.guardrail.SecurityEventType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggingSecurityEventSinkTest {

    private LoggingSecurityEventSink sink;

    private static SecurityEvent event(SecurityEventType type) {
        return new SecurityEvent(
                Instant.now(),
                type,
                "test-agent",
                "target-tool",
                GuardrailPhase.PRE_TOOL,
                "test-policy",
                "test reason",
                Map.of());
    }

    @BeforeEach
    void setUp() {
        sink = new LoggingSecurityEventSink();
    }

    @Test
    void implementsSecurityEventSink() {
        assertInstanceOf(SecurityEventSink.class, sink);
    }

    @Test
    void recordGuardrailDenyDoesNotThrow() {
        assertDoesNotThrow(() -> sink.record(event(SecurityEventType.GUARDRAIL_DENY)));
    }

    @Test
    void recordMcpBlockDoesNotThrow() {
        assertDoesNotThrow(() -> sink.record(event(SecurityEventType.MCP_BLOCK)));
    }

    @Test
    void recordGuardrailAllowDoesNotThrow() {
        assertDoesNotThrow(() -> sink.record(event(SecurityEventType.GUARDRAIL_ALLOW)));
    }

    @Test
    void recordGuardrailWarnDoesNotThrow() {
        assertDoesNotThrow(() -> sink.record(event(SecurityEventType.GUARDRAIL_WARN)));
    }

    @Test
    void recordGuardrailModifyDoesNotThrow() {
        assertDoesNotThrow(() -> sink.record(event(SecurityEventType.GUARDRAIL_MODIFY)));
    }

    @Test
    void recordWithNullReasonDoesNotThrow() {
        SecurityEvent e =
                new SecurityEvent(
                        Instant.now(),
                        SecurityEventType.GUARDRAIL_DENY,
                        "agent",
                        "target",
                        GuardrailPhase.POST_MODEL,
                        "policy",
                        null,
                        Map.of());
        assertDoesNotThrow(() -> sink.record(e));
    }
}
