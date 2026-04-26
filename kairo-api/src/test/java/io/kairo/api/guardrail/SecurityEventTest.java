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
package io.kairo.api.guardrail;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityEventTest {

    private static final GuardrailPayload.ToolInput SAMPLE_PAYLOAD =
            new GuardrailPayload.ToolInput("echo", Map.of("text", "hello"));

    private static final GuardrailContext SAMPLE_CONTEXT =
            new GuardrailContext(
                    GuardrailPhase.PRE_TOOL, "test-agent", "echo", SAMPLE_PAYLOAD, Map.of());

    @Test
    void securityEventRecordConstruction() {
        Instant now = Instant.now();
        SecurityEvent event =
                new SecurityEvent(
                        now,
                        SecurityEventType.GUARDRAIL_ALLOW,
                        "agent-1",
                        "tool-a",
                        GuardrailPhase.PRE_TOOL,
                        "my-policy",
                        "allowed",
                        Map.of("key", (Object) "value"));

        assertEquals(now, event.timestamp());
        assertEquals(SecurityEventType.GUARDRAIL_ALLOW, event.type());
        assertEquals("agent-1", event.agentName());
        assertEquals("tool-a", event.targetName());
        assertEquals(GuardrailPhase.PRE_TOOL, event.phase());
        assertEquals("my-policy", event.policyName());
        assertEquals("allowed", event.reason());
        assertEquals(Map.of("key", "value"), event.attributes());
    }

    @Test
    void securityEventTypeHasExactlySixValues() {
        SecurityEventType[] values = SecurityEventType.values();
        assertEquals(6, values.length);
        assertNotNull(SecurityEventType.GUARDRAIL_ALLOW);
        assertNotNull(SecurityEventType.GUARDRAIL_DENY);
        assertNotNull(SecurityEventType.GUARDRAIL_MODIFY);
        assertNotNull(SecurityEventType.GUARDRAIL_WARN);
        assertNotNull(SecurityEventType.PERMISSION_DENY);
        assertNotNull(SecurityEventType.MCP_BLOCK);
    }

    @Test
    void fromDecisionMapsAllowCorrectly() {
        GuardrailDecision decision = GuardrailDecision.allow("pass-policy");
        SecurityEvent event = SecurityEvent.fromDecision(decision, SAMPLE_CONTEXT);

        assertEquals(SecurityEventType.GUARDRAIL_ALLOW, event.type());
        assertEquals("test-agent", event.agentName());
        assertEquals("echo", event.targetName());
        assertEquals(GuardrailPhase.PRE_TOOL, event.phase());
        assertEquals("pass-policy", event.policyName());
        assertEquals("allowed", event.reason());
        assertTrue(event.attributes().isEmpty());
        assertNotNull(event.timestamp());
    }

    @Test
    void fromDecisionMapsDenyCorrectly() {
        GuardrailDecision decision = GuardrailDecision.deny("blocked", "content-filter");
        SecurityEvent event = SecurityEvent.fromDecision(decision, SAMPLE_CONTEXT);

        assertEquals(SecurityEventType.GUARDRAIL_DENY, event.type());
        assertEquals("content-filter", event.policyName());
        assertEquals("blocked", event.reason());
    }

    @Test
    void fromDecisionMapsModifyCorrectly() {
        GuardrailPayload.ToolInput modified =
                new GuardrailPayload.ToolInput("echo", Map.of("text", "REDACTED"));
        GuardrailDecision decision =
                GuardrailDecision.modify(modified, "redacted PII", "pii-filter");
        SecurityEvent event = SecurityEvent.fromDecision(decision, SAMPLE_CONTEXT);

        assertEquals(SecurityEventType.GUARDRAIL_MODIFY, event.type());
        assertEquals("pii-filter", event.policyName());
        assertEquals("redacted PII", event.reason());
    }

    @Test
    void fromDecisionMapsWarnCorrectly() {
        GuardrailDecision decision = GuardrailDecision.warn("suspicious", "anomaly-detector");
        SecurityEvent event = SecurityEvent.fromDecision(decision, SAMPLE_CONTEXT);

        assertEquals(SecurityEventType.GUARDRAIL_WARN, event.type());
        assertEquals("anomaly-detector", event.policyName());
        assertEquals("suspicious", event.reason());
    }
}
