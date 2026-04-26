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

import static org.assertj.core.api.Assertions.assertThatCode;

import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.guardrail.SecurityEventSink;
import io.kairo.api.guardrail.SecurityEventType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoggingSecurityEventSinkTest {

    private final LoggingSecurityEventSink sink = new LoggingSecurityEventSink();

    private static SecurityEvent event(SecurityEventType type) {
        return new SecurityEvent(
                Instant.now(),
                type,
                "agent",
                "tool",
                GuardrailPhase.PRE_MODEL,
                "policy",
                "",
                Map.of());
    }

    @Test
    void implementsSecurityEventSink() {
        assertThatCode(
                        () -> {
                            SecurityEventSink s = sink;
                            s.record(event(SecurityEventType.GUARDRAIL_ALLOW));
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void recordAllowDoesNotThrow() {
        assertThatCode(() -> sink.record(event(SecurityEventType.GUARDRAIL_ALLOW)))
                .doesNotThrowAnyException();
    }

    @Test
    void recordDenyDoesNotThrow() {
        assertThatCode(() -> sink.record(event(SecurityEventType.GUARDRAIL_DENY)))
                .doesNotThrowAnyException();
    }

    @Test
    void recordModifyDoesNotThrow() {
        assertThatCode(() -> sink.record(event(SecurityEventType.GUARDRAIL_MODIFY)))
                .doesNotThrowAnyException();
    }

    @Test
    void recordWarnDoesNotThrow() {
        assertThatCode(() -> sink.record(event(SecurityEventType.GUARDRAIL_WARN)))
                .doesNotThrowAnyException();
    }

    @Test
    void recordMcpBlockDoesNotThrow() {
        assertThatCode(() -> sink.record(event(SecurityEventType.MCP_BLOCK)))
                .doesNotThrowAnyException();
    }
}
