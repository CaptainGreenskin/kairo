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

class DefaultGuardrailChainTest {

    private static final GuardrailPayload.ToolInput SAMPLE_PAYLOAD =
            new GuardrailPayload.ToolInput("echo", Map.of("text", "hello"));

    private static final GuardrailContext SAMPLE_CONTEXT =
            new GuardrailContext(
                    GuardrailPhase.PRE_TOOL, "test-agent", "echo", SAMPLE_PAYLOAD, Map.of());

    // --- Empty chain ---

    @Test
    void emptyChainReturnsAllow() {
        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of());

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(
                        d -> {
                            assertEquals(GuardrailDecision.Action.ALLOW, d.action());
                            assertEquals("no-policy", d.policyName());
                        })
                .verifyComplete();
    }

    // --- Single policy ---

    @Test
    void singleAllowPolicyReturnsAllow() {
        GuardrailPolicy allow = allowPolicy("pass-policy");
        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(allow));

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(d -> assertEquals(GuardrailDecision.Action.ALLOW, d.action()))
                .verifyComplete();
    }

    @Test
    void singleDenyPolicyReturnsDenyWithCorrectFields() {
        GuardrailPolicy deny = denyPolicy("blocked", "content-filter");
        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(deny));

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(
                        d -> {
                            assertEquals(GuardrailDecision.Action.DENY, d.action());
                            assertEquals("blocked", d.reason());
                            assertEquals("content-filter", d.policyName());
                        })
                .verifyComplete();
    }

    // --- Ordering ---

    @Test
    void policiesEvaluatedInOrderByOrderValue() {
        List<String> evaluationOrder = new ArrayList<>();

        GuardrailPolicy first = trackingPolicy("first", 10, evaluationOrder);
        GuardrailPolicy second = trackingPolicy("second", 20, evaluationOrder);
        GuardrailPolicy third = trackingPolicy("third", 5, evaluationOrder);

        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(first, second, third));

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT)).expectNextCount(1).verifyComplete();

        assertEquals(List.of("third", "first", "second"), evaluationOrder);
    }

    // --- DENY short-circuit ---

    @Test
    void denyShortCircuitsRemainingPolicies() {
        AtomicBoolean secondEvaluated = new AtomicBoolean(false);

        GuardrailPolicy deny = denyPolicy("halt", "blocker");
        GuardrailPolicy after =
                ctx -> {
                    secondEvaluated.set(true);
                    return Mono.just(GuardrailDecision.allow("after"));
                };

        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(deny, after));

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(d -> assertEquals(GuardrailDecision.Action.DENY, d.action()))
                .verifyComplete();

        assertFalse(secondEvaluated.get(), "Policy after DENY should not be evaluated");
    }

    // --- MODIFY propagation ---

    @Test
    void modifyUpdatesPayloadForSubsequentPolicies() {
        GuardrailPayload.ToolInput modified =
                new GuardrailPayload.ToolInput("echo", Map.of("text", "REDACTED"));

        GuardrailPolicy modifier =
                ctx -> Mono.just(GuardrailDecision.modify(modified, "redacted PII", "pii-filter"));

        // Second policy captures the context it receives
        List<GuardrailPayload> seen = new ArrayList<>();
        GuardrailPolicy observer =
                ctx -> {
                    seen.add(ctx.payload());
                    return Mono.just(GuardrailDecision.allow("observer"));
                };

        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(modifier, observer));

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT)).expectNextCount(1).verifyComplete();

        assertEquals(1, seen.size());
        assertSame(modified, seen.get(0));
    }

    // --- WARN continues ---

    @Test
    void warnLogsButContinuesChain() {
        GuardrailPolicy warner =
                ctx -> Mono.just(GuardrailDecision.warn("suspicious", "anomaly-detector"));
        GuardrailPolicy allow = allowPolicy("final-pass");

        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(warner, allow));

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(d -> assertEquals(GuardrailDecision.Action.ALLOW, d.action()))
                .verifyComplete();
    }

    // --- Mixed chain ---

    @Test
    void mixedChainAllowWarnAllowReturnsAllow() {
        GuardrailPolicy p1 = allowPolicy("first");
        GuardrailPolicy p2 = ctx -> Mono.just(GuardrailDecision.warn("heads up", "warner"));
        GuardrailPolicy p3 = allowPolicy("third");

        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(p1, p2, p3));

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(d -> assertEquals(GuardrailDecision.Action.ALLOW, d.action()))
                .verifyComplete();
    }

    @Test
    void denyInMiddleOfChainStopsEvaluation() {
        AtomicBoolean thirdEvaluated = new AtomicBoolean(false);

        GuardrailPolicy p1 = allowPolicy("first");
        GuardrailPolicy p2 = denyPolicy("stop here", "mid-blocker");
        GuardrailPolicy p3 =
                ctx -> {
                    thirdEvaluated.set(true);
                    return Mono.just(GuardrailDecision.allow("third"));
                };

        DefaultGuardrailChain chain = new DefaultGuardrailChain(List.of(p1, p2, p3));

        StepVerifier.create(chain.evaluate(SAMPLE_CONTEXT))
                .assertNext(
                        d -> {
                            assertEquals(GuardrailDecision.Action.DENY, d.action());
                            assertEquals("stop here", d.reason());
                            assertEquals("mid-blocker", d.policyName());
                        })
                .verifyComplete();

        assertFalse(thirdEvaluated.get(), "Third policy should not be evaluated after DENY");
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

    private static GuardrailPolicy trackingPolicy(
            String name, int order, List<String> evaluationOrder) {
        return new GuardrailPolicy() {
            @Override
            public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                evaluationOrder.add(name);
                return Mono.just(GuardrailDecision.allow(name));
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}
