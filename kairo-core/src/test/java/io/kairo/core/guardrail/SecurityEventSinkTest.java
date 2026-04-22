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

import io.kairo.api.guardrail.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SecurityEventSinkTest {

    private static final GuardrailPayload.ToolInput SAMPLE_PAYLOAD =
            new GuardrailPayload.ToolInput("echo", Map.of("text", "hello"));

    private static final GuardrailContext SAMPLE_CONTEXT =
            new GuardrailContext(
                    GuardrailPhase.PRE_TOOL, "test-agent", "echo", SAMPLE_PAYLOAD, Map.of());

    // --- LoggingSecurityEventSink ---

    @Test
    void loggingSecurityEventSinkRecordsWithoutError() {
        LoggingSecurityEventSink sink = new LoggingSecurityEventSink();
        SecurityEvent event =
                SecurityEvent.fromDecision(GuardrailDecision.allow("test-policy"), SAMPLE_CONTEXT);

        assertDoesNotThrow(() -> sink.record(event));
    }

    @Test
    void loggingSecurityEventSinkHandlesDenyEvent() {
        LoggingSecurityEventSink sink = new LoggingSecurityEventSink();
        SecurityEvent event =
                SecurityEvent.fromDecision(
                        GuardrailDecision.deny("blocked", "content-filter"), SAMPLE_CONTEXT);

        assertDoesNotThrow(() -> sink.record(event));
    }

    // --- DefaultGuardrailChain event emission ---

    @Test
    void chainEmitsEventToSinkForEachPolicy() {
        List<SecurityEvent> recorded = new ArrayList<>();
        SecurityEventSink sink = recorded::add;

        GuardrailPolicy p1 = allowPolicy("first");
        GuardrailPolicy p2 = allowPolicy("second");
        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(p1, p2), sink);

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT)).expectNextCount(1).verifyComplete();

        assertEquals(2, recorded.size());
        assertEquals(SecurityEventType.GUARDRAIL_ALLOW, recorded.get(0).type());
        assertEquals("first", recorded.get(0).policyName());
        assertEquals(SecurityEventType.GUARDRAIL_ALLOW, recorded.get(1).type());
        assertEquals("second", recorded.get(1).policyName());
    }

    @Test
    void chainEmitsDenyEventOnShortCircuit() {
        List<SecurityEvent> recorded = new ArrayList<>();
        SecurityEventSink sink = recorded::add;

        AtomicBoolean secondEvaluated = new AtomicBoolean(false);
        GuardrailPolicy deny = denyPolicy("halt", "blocker");
        GuardrailPolicy after =
                ctx -> {
                    secondEvaluated.set(true);
                    return Mono.just(GuardrailDecision.allow("after"));
                };

        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(deny, after), sink);

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(d -> assertEquals(GuardrailDecision.Action.DENY, d.action()))
                .verifyComplete();

        // DENY event is emitted even though chain short-circuits
        assertEquals(1, recorded.size());
        assertEquals(SecurityEventType.GUARDRAIL_DENY, recorded.get(0).type());
        assertEquals("blocker", recorded.get(0).policyName());
        assertFalse(secondEvaluated.get());
    }

    @Test
    void chainEmitsEventsForMixedDecisions() {
        List<SecurityEvent> recorded = new ArrayList<>();
        SecurityEventSink sink = recorded::add;

        GuardrailPolicy warn =
                ctx -> Mono.just(GuardrailDecision.warn("suspicious", "anomaly-detector"));
        GuardrailPolicy allow = allowPolicy("final-pass");
        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(warn, allow), sink);

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT)).expectNextCount(1).verifyComplete();

        assertEquals(2, recorded.size());
        assertEquals(SecurityEventType.GUARDRAIL_WARN, recorded.get(0).type());
        assertEquals(SecurityEventType.GUARDRAIL_ALLOW, recorded.get(1).type());
    }

    @Test
    void chainEmitsEventForModifyDecision() {
        List<SecurityEvent> recorded = new ArrayList<>();
        SecurityEventSink sink = recorded::add;

        GuardrailPayload.ToolInput modified =
                new GuardrailPayload.ToolInput("echo", Map.of("text", "REDACTED"));
        GuardrailPolicy modifier =
                ctx -> Mono.just(GuardrailDecision.modify(modified, "redacted PII", "pii-filter"));
        GuardrailPolicy allow = allowPolicy("final");
        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(modifier, allow), sink);

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT)).expectNextCount(1).verifyComplete();

        assertEquals(2, recorded.size());
        assertEquals(SecurityEventType.GUARDRAIL_MODIFY, recorded.get(0).type());
        assertEquals("pii-filter", recorded.get(0).policyName());
    }

    @Test
    void nullSinkDoesNotCauseErrors() {
        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(allowPolicy("pass")), null);

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(d -> assertEquals(GuardrailDecision.Action.ALLOW, d.action()))
                .verifyComplete();
    }

    @Test
    void noArgConstructorDoesNotCauseErrors() {
        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(allowPolicy("pass")));

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(d -> assertEquals(GuardrailDecision.Action.ALLOW, d.action()))
                .verifyComplete();
    }

    @Test
    void securityEventHasCorrectFieldsFromContext() {
        List<SecurityEvent> recorded = new ArrayList<>();
        SecurityEventSink sink = recorded::add;

        GuardrailPolicy allow = allowPolicy("ctx-policy");
        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(allow), sink);

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT)).expectNextCount(1).verifyComplete();

        SecurityEvent event = recorded.get(0);
        assertEquals("test-agent", event.agentName());
        assertEquals("echo", event.targetName());
        assertEquals(GuardrailPhase.PRE_TOOL, event.phase());
        assertEquals("ctx-policy", event.policyName());
        assertNotNull(event.timestamp());
        assertTrue(event.attributes().isEmpty());
    }

    // --- helpers ---

    private static GuardrailPolicy allowPolicy(String name) {
        return new GuardrailPolicy() {
            @Override
            public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                return Mono.just(GuardrailDecision.allow(name));
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    private static GuardrailPolicy denyPolicy(String reason, String name) {
        return new GuardrailPolicy() {
            @Override
            public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                return Mono.just(GuardrailDecision.deny(reason, name));
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}
