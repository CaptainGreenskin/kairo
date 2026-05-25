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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.guardrail.GuardrailContext;
import io.kairo.api.guardrail.GuardrailDecision;
import io.kairo.api.guardrail.GuardrailPayload;
import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.GuardrailPolicy;
import io.kairo.api.tracing.NoopSpan;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Locks in the observability contract for {@link DefaultGuardrailChain}: one span per policy, the
 * span carries {@code guardrail.*} metadata + Langfuse {@code observation.*} attributes, DENY
 * decisions surface as ERROR level, exception in a policy still allows the chain to keep going, and
 * {@link DefaultGuardrailChain#snapshot()} reflects what happened.
 */
class DefaultGuardrailChainObservabilityTest {

    private GuardrailContext ctx() {
        return new GuardrailContext(
                GuardrailPhase.PRE_TOOL,
                "agent-a",
                "bash",
                new GuardrailPayload.ToolInput("bash", Map.of("cmd", "ls")),
                Map.of());
    }

    private static GuardrailPolicy policy(String name, GuardrailDecision decision) {
        return new GuardrailPolicy() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                return Mono.just(decision);
            }
        };
    }

    private static GuardrailPolicy throwingPolicy(String name, RuntimeException ex) {
        return new GuardrailPolicy() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                return Mono.error(ex);
            }
        };
    }

    @Test
    void singleAllowPolicy_emitsOneSpanWithLangfuseAttributes() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultGuardrailChain chain =
                new DefaultGuardrailChain(
                        List.of(policy("allowAll", GuardrailDecision.allow("allowAll"))),
                        null,
                        tracer);

        StepVerifier.create(chain.evaluate(ctx())).expectNextCount(1).verifyComplete();

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.attributes)
                .containsEntry("langfuse.observation.type", "span")
                .containsEntry("langfuse.observation.level", "DEFAULT")
                .containsEntry("guardrail.policy_name", "allowAll")
                .containsEntry("guardrail.phase", "PRE_TOOL")
                .containsEntry("guardrail.action", "ALLOW")
                .containsKey("guardrail.duration_ms");
        assertThat(span.statusSuccess).isTrue();
        assertThat(span.ended).isTrue();

        DefaultGuardrailChain.GuardrailChainStats stats = chain.snapshot();
        assertThat(stats.evaluationsByPolicy()).containsEntry("allowAll", 1L);
        assertThat(stats.decisionsByAction()).containsEntry("ALLOW", 1L);
        assertThat(stats.failuresByPolicy()).isEmpty();
    }

    @Test
    void denyDecision_surfacesAsErrorLevel() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultGuardrailChain chain =
                new DefaultGuardrailChain(
                        List.of(policy("denyBoom", GuardrailDecision.deny("blocked", "denyBoom"))),
                        null,
                        tracer);

        StepVerifier.create(chain.evaluate(ctx()))
                .assertNext(
                        d -> {
                            assertThat(d.action()).isEqualTo(GuardrailDecision.Action.DENY);
                            assertThat(d.reason()).isEqualTo("blocked");
                        })
                .verifyComplete();

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.attributes)
                .containsEntry("guardrail.action", "DENY")
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("langfuse.observation.status_message", "blocked");

        DefaultGuardrailChain.GuardrailChainStats stats = chain.snapshot();
        assertThat(stats.decisionsByAction()).containsEntry("DENY", 1L);
    }

    @Test
    void warnDecision_surfacesAsWarningLevel_chainContinues() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultGuardrailChain chain =
                new DefaultGuardrailChain(
                        List.of(
                                policy("warner", GuardrailDecision.warn("careful", "warner")),
                                policy("nextAllow", GuardrailDecision.allow("nextAllow"))),
                        null,
                        tracer);

        StepVerifier.create(chain.evaluate(ctx())).expectNextCount(1).verifyComplete();

        assertThat(tracer.spans).hasSize(2);
        RecordingSpan warnSpan = tracer.spans.get(0);
        assertThat(warnSpan.attributes)
                .containsEntry("guardrail.action", "WARN")
                .containsEntry("langfuse.observation.level", "WARNING");

        DefaultGuardrailChain.GuardrailChainStats stats = chain.snapshot();
        assertThat(stats.decisionsByAction()).containsEntry("WARN", 1L).containsEntry("ALLOW", 1L);
        assertThat(stats.evaluationsByPolicy())
                .containsEntry("warner", 1L)
                .containsEntry("nextAllow", 1L);
    }

    @Test
    void denyShortCircuits_subsequentPoliciesNotEvaluated() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultGuardrailChain chain =
                new DefaultGuardrailChain(
                        List.of(
                                policy("first", GuardrailDecision.deny("nope", "first")),
                                policy("second", GuardrailDecision.allow("second"))),
                        null,
                        tracer);

        StepVerifier.create(chain.evaluate(ctx()))
                .assertNext(d -> assertThat(d.action()).isEqualTo(GuardrailDecision.Action.DENY))
                .verifyComplete();

        assertThat(tracer.spans).hasSize(1);
        assertThat(chain.snapshot().evaluationsByPolicy())
                .containsEntry("first", 1L)
                .doesNotContainKey("second");
    }

    @Test
    void policyThrows_recordsFailureAndChainContinues() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultGuardrailChain chain =
                new DefaultGuardrailChain(
                        List.of(
                                throwingPolicy("boomPolicy", new IllegalStateException("kaboom")),
                                policy("safety", GuardrailDecision.allow("safety"))),
                        null,
                        tracer);

        StepVerifier.create(chain.evaluate(ctx())).expectNextCount(1).verifyComplete();

        assertThat(tracer.spans).hasSize(2);
        RecordingSpan failSpan = tracer.spans.get(0);
        assertThat(failSpan.attributes)
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("guardrail.policy_name", "boomPolicy");
        assertThat((String) failSpan.attributes.get("langfuse.observation.status_message"))
                .contains("IllegalStateException")
                .contains("kaboom");
        assertThat(failSpan.statusSuccess).isFalse();

        DefaultGuardrailChain.GuardrailChainStats stats = chain.snapshot();
        assertThat(stats.failuresByPolicy()).containsEntry("boomPolicy", 1L);
        assertThat(stats.decisionsByAction()).containsEntry("ERROR", 1L);
        assertThat(stats.evaluationsByPolicy())
                .containsEntry("boomPolicy", 1L)
                .containsEntry("safety", 1L);
    }

    @Test
    void noTracerConstructor_isNoOpButCountersStillWork() {
        DefaultGuardrailChain chain =
                new DefaultGuardrailChain(
                        List.of(policy("allowAll", GuardrailDecision.allow("allowAll"))));

        StepVerifier.create(chain.evaluate(ctx())).expectNextCount(1).verifyComplete();

        // No tracer to assert on, but snapshot must still reflect the activity.
        assertThat(chain.snapshot().evaluationsByPolicy()).containsEntry("allowAll", 1L);
        assertThat(chain.snapshot().decisionsByAction()).containsEntry("ALLOW", 1L);
    }

    // ── Recording scaffolding (same shape as DefaultHookChainObservabilityTest) ─────

    private static final class RecordingTracer implements Tracer {
        final List<RecordingSpan> spans = new ArrayList<>();

        @Override
        public Span startGuardrailSpan(Span parent, String policyName, String phase) {
            RecordingSpan span = new RecordingSpan("guardrail:" + phase + ":" + policyName);
            spans.add(span);
            return span;
        }
    }

    private static final class RecordingSpan implements Span {
        final String name;
        final Map<String, Object> attributes = new HashMap<>();
        boolean statusSuccess;
        String statusMessage;
        boolean ended;

        RecordingSpan(String name) {
            this.name = name;
        }

        @Override
        public String spanId() {
            return "test-span";
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Span parent() {
            return NoopSpan.INSTANCE;
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public void setStatus(boolean success, String message) {
            this.statusSuccess = success;
            this.statusMessage = message;
        }

        @Override
        public void end() {
            this.ended = true;
        }
    }
}
