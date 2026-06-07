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

import io.kairo.api.guardrail.*;
import io.kairo.api.tracing.NoopTracer;
import io.kairo.api.tracing.ObservationData;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default ordered chain evaluator for {@link GuardrailPolicy} instances.
 *
 * <p>Policies are sorted by {@link GuardrailPolicy#order()} and evaluated sequentially using an
 * iterative {@link Flux#reduce} pattern (no recursion), eliminating stack-overflow risk for large
 * policy lists.
 *
 * <ul>
 *   <li>{@link GuardrailDecision.Action#DENY} — short-circuits; remaining policies are skipped.
 *   <li>{@link GuardrailDecision.Action#MODIFY} — replaces the payload; subsequent policies see the
 *       modified version.
 *   <li>{@link GuardrailDecision.Action#WARN} — logged; chain continues.
 *   <li>{@link GuardrailDecision.Action#ALLOW} — chain continues unchanged.
 * </ul>
 *
 * <p>An empty chain returns {@link GuardrailDecision#allow(String)} with policy name {@code
 * "no-policy"} — zero overhead when no policies are registered.
 *
 * <p><strong>Observability:</strong> when constructed with a {@link Tracer}, each policy evaluation
 * opens one child span via {@code tracer.startGuardrailSpan(parent, policyName, phase)} and a
 * structured {@link ObservationData} attribute set ({@code guardrail.*} + Langfuse {@code
 * observation.*}) is written through {@link Tracer#recordObservation}. AtomicLong counters power
 * the programmatic {@link GuardrailChainStats} snapshot consumed by tests and the {@code
 * :guardrails} REPL surface, mirroring the {@code LlmBashClassifier.Stats} model-citizen pattern.
 */
public class DefaultGuardrailChain implements GuardrailChain {

    private static final Logger log = LoggerFactory.getLogger(DefaultGuardrailChain.class);

    /** Maximum number of policies allowed in a single chain. */
    static final int MAX_POLICIES = 64;

    /**
     * Reactor Context key for the parent span — matches {@code
     * DefaultToolExecutor.SPAN_CONTEXT_KEY} and {@code DefaultHookChain.SPAN_CONTEXT_KEY} so
     * existing callers populating {@code Context.put(Span.class, …)} get guardrail spans nested for
     * free.
     */
    public static final Class<Span> SPAN_CONTEXT_KEY = Span.class;

    private final ArrayList<GuardrailPolicy> policies;
    private final SecurityEventSink sink;
    private final Tracer tracer;

    private final Map<String, AtomicLong> decisionsByAction = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> evaluationsByPolicy = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failuresByPolicy = new ConcurrentHashMap<>();
    private final AtomicLong totalDurationNanos = new AtomicLong();

    /** Creates a chain with no event sink and no tracer (backward-compatible). */
    public DefaultGuardrailChain(List<GuardrailPolicy> policies) {
        this(policies, null, NoopTracer.INSTANCE);
    }

    /**
     * Creates a chain that emits {@link SecurityEvent}s to the given sink after each policy
     * evaluation. Spans are still no-ops (use the 3-arg constructor to opt into tracing).
     */
    public DefaultGuardrailChain(List<GuardrailPolicy> policies, SecurityEventSink sink) {
        this(policies, sink, NoopTracer.INSTANCE);
    }

    /**
     * Creates a chain that emits {@link SecurityEvent}s to the given sink and opens one child span
     * per policy evaluation via {@link Tracer#startGuardrailSpan(Span, String, String)}.
     *
     * @param policies the ordered list of guardrail policies
     * @param sink the event sink (may be {@code null} to disable event emission)
     * @param tracer the tracer (may be {@code null} to fall back to {@link NoopTracer#INSTANCE})
     * @throws IllegalArgumentException if the number of policies exceeds {@link #MAX_POLICIES}
     */
    public DefaultGuardrailChain(
            List<GuardrailPolicy> policies, SecurityEventSink sink, Tracer tracer) {
        if (policies.size() > MAX_POLICIES) {
            throw new IllegalArgumentException(
                    "Too many guardrail policies: "
                            + policies.size()
                            + " exceeds maximum of "
                            + MAX_POLICIES);
        }
        this.policies =
                new ArrayList<>(
                        policies.stream()
                                .sorted(Comparator.comparingInt(GuardrailPolicy::order))
                                .toList());
        this.sink = sink;
        this.tracer = tracer == null ? NoopTracer.INSTANCE : tracer;
    }

    /**
     * Append a policy to this chain after construction. Used by kairo-code to inject
     * context-specific policies (e.g. BashWriteGuard for coordinator mode) that aren't known at
     * chain-construction time.
     */
    public void addPolicy(GuardrailPolicy policy) {
        if (policies.size() >= MAX_POLICIES) {
            throw new IllegalStateException("GuardrailChain policy limit reached: " + MAX_POLICIES);
        }
        policies.add(policy);
        policies.sort(Comparator.comparingInt(GuardrailPolicy::order));
    }

    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        if (policies.isEmpty()) {
            return Mono.just(GuardrailDecision.allow("no-policy"));
        }
        return evaluateIterative(context);
    }

    /**
     * Iterative chain evaluation using reactive {@link Flux#concatMap} + {@link Mono#reduce}. Each
     * policy is evaluated in order; DENY short-circuits via a sentinel wrapper, MODIFY propagates
     * the modified payload to subsequent policies, and WARN/ALLOW continue unchanged.
     */
    private Mono<GuardrailDecision> evaluateIterative(GuardrailContext context) {
        final GuardrailPayload originalPayload = context.payload();

        return Mono.deferContextual(
                ctxView -> {
                    Span parent =
                            ctxView.hasKey(SPAN_CONTEXT_KEY) ? ctxView.get(SPAN_CONTEXT_KEY) : null;
                    return Flux.fromIterable(policies)
                            .reduce(
                                    Mono.just(new ChainState(context, null)),
                                    (stateMono, policy) ->
                                            stateMono.flatMap(
                                                    state -> {
                                                        if (state.shortCircuit() != null) {
                                                            return Mono.just(state);
                                                        }
                                                        return evaluateOne(
                                                                        policy, state.ctx(), parent)
                                                                .map(
                                                                        decision ->
                                                                                applyDecision(
                                                                                        state,
                                                                                        decision));
                                                    }))
                            .flatMap(mono -> mono)
                            .map(
                                    state -> {
                                        if (state.shortCircuit() != null) {
                                            return state.shortCircuit();
                                        }
                                        if (state.ctx().payload() != originalPayload) {
                                            return GuardrailDecision.modify(
                                                    state.ctx().payload(),
                                                    "chain-modified",
                                                    "chain-complete");
                                        }
                                        return GuardrailDecision.allow("chain-complete");
                                    });
                });
    }

    /** Accumulator passed through {@link Flux#reduce}: current context + optional short-circuit. */
    private record ChainState(GuardrailContext ctx, GuardrailDecision shortCircuit) {}

    /**
     * Evaluate a single policy with full instrumentation: opens a guardrail span, records the
     * decision (or failure) attributes via {@link Tracer#recordObservation}, bumps the per-policy
     * and per-action counters, and emits a {@link SecurityEvent} when a sink is wired.
     *
     * <p>Returns a defensive {@code allow("policy-error")} on policy exceptions so the chain keeps
     * evaluating — observability is opt-out only by replacing the chain itself.
     */
    private Mono<GuardrailDecision> evaluateOne(
            GuardrailPolicy policy, GuardrailContext context, Span parent) {
        String policyName = policy.name();
        String phaseLabel = context.phase() == null ? "UNKNOWN" : context.phase().name();
        Span span = tracer.startGuardrailSpan(parent, policyName, phaseLabel);
        long start = System.nanoTime();

        return policy.evaluate(context)
                .switchIfEmpty(Mono.just(GuardrailDecision.allow("no-decision")))
                .doOnNext(
                        decision -> {
                            long elapsedNanos = System.nanoTime() - start;
                            recordSuccess(span, policyName, phaseLabel, decision, elapsedNanos);
                            emitEvent(decision, context);
                        })
                .doOnError(
                        error -> {
                            long elapsedNanos = System.nanoTime() - start;
                            recordFailure(span, policyName, phaseLabel, error, elapsedNanos);
                        })
                .onErrorResume(
                        error -> {
                            // Failures must not break the chain — surface as ALLOW with marker
                            // reason and let downstream policies + caller-side defaults decide.
                            log.error("Guardrail policy {} threw an exception", policyName, error);
                            return Mono.just(GuardrailDecision.allow(policyName + ":policy-error"));
                        });
    }

    /** Fold a fresh {@link GuardrailDecision} into the chain state. */
    private static ChainState applyDecision(ChainState state, GuardrailDecision decision) {
        GuardrailContext ctx = state.ctx();
        return switch (decision.action()) {
            case DENY -> new ChainState(ctx, decision);
            case MODIFY -> {
                GuardrailContext modified =
                        new GuardrailContext(
                                ctx.phase(),
                                ctx.agentName(),
                                ctx.targetName(),
                                decision.modifiedPayload(),
                                Map.copyOf(ctx.metadata()));
                yield new ChainState(modified, null);
            }
            case WARN -> {
                log.warn("Guardrail warning from {}: {}", decision.policyName(), decision.reason());
                yield state;
            }
            default -> state;
        };
    }

    private void emitEvent(GuardrailDecision decision, GuardrailContext context) {
        if (sink != null) {
            SecurityEvent event = SecurityEvent.fromDecision(decision, context);
            sink.record(event);
        }
    }

    // ==================== OBSERVATION ====================

    private void recordSuccess(
            Span span,
            String policyName,
            String phase,
            GuardrailDecision decision,
            long elapsedNanos) {
        String action = decision.action().name();
        evaluationsByPolicy.computeIfAbsent(policyName, k -> new AtomicLong()).incrementAndGet();
        decisionsByAction.computeIfAbsent(action, k -> new AtomicLong()).incrementAndGet();
        totalDurationNanos.addAndGet(elapsedNanos);
        long elapsedMs = elapsedNanos / 1_000_000L;

        Map<String, Object> meta = new HashMap<>();
        meta.put("guardrail.policy_name", policyName);
        meta.put("guardrail.phase", phase);
        meta.put("guardrail.action", action);
        meta.put("guardrail.duration_ms", elapsedMs);
        if (decision.reason() != null) meta.put("guardrail.reason", decision.reason());

        ObservationData.Level level =
                decision.action() == GuardrailDecision.Action.DENY
                        ? ObservationData.Level.ERROR
                        : decision.action() == GuardrailDecision.Action.WARN
                                ? ObservationData.Level.WARNING
                                : ObservationData.Level.DEFAULT;

        tracer.recordObservation(
                span,
                ObservationData.builder()
                        .type(ObservationData.Type.SPAN)
                        .level(level)
                        .statusMessage(
                                decision.action() == GuardrailDecision.Action.DENY
                                        ? decision.reason()
                                        : null)
                        .metadata(meta)
                        .build());
        span.setStatus(true, decision.reason());
        span.end();
    }

    private void recordFailure(
            Span span, String policyName, String phase, Throwable error, long elapsedNanos) {
        evaluationsByPolicy.computeIfAbsent(policyName, k -> new AtomicLong()).incrementAndGet();
        failuresByPolicy.computeIfAbsent(policyName, k -> new AtomicLong()).incrementAndGet();
        decisionsByAction.computeIfAbsent("ERROR", k -> new AtomicLong()).incrementAndGet();
        totalDurationNanos.addAndGet(elapsedNanos);
        long elapsedMs = elapsedNanos / 1_000_000L;

        Map<String, Object> meta = new HashMap<>();
        meta.put("guardrail.policy_name", policyName);
        meta.put("guardrail.phase", phase);
        meta.put("guardrail.duration_ms", elapsedMs);

        tracer.recordObservation(
                span,
                ObservationData.builder()
                        .type(ObservationData.Type.SPAN)
                        .level(ObservationData.Level.ERROR)
                        .statusMessage(error.getClass().getSimpleName() + ": " + error.getMessage())
                        .metadata(meta)
                        .build());
        span.setStatus(false, error.getMessage());
        span.end();
    }

    /**
     * Snapshot the in-memory counters as an immutable {@link GuardrailChainStats}. Used by tests
     * and REPL surfaces — values are point-in-time copies, not live views.
     */
    public GuardrailChainStats snapshot() {
        return new GuardrailChainStats(
                snapshotMap(evaluationsByPolicy),
                snapshotMap(decisionsByAction),
                snapshotMap(failuresByPolicy),
                totalDurationNanos.get() / 1_000_000L);
    }

    private Map<String, Long> snapshotMap(Map<String, AtomicLong> source) {
        Map<String, Long> out = new HashMap<>(source.size());
        source.forEach((k, v) -> out.put(k, v.get()));
        return Map.copyOf(out);
    }

    /**
     * Programmatic observation snapshot for the guardrail chain. Returned by {@link #snapshot()};
     * exposes per-policy evaluation counts, per-action decision counts, per-policy failure counts,
     * and total wall-clock duration across all evaluations.
     *
     * @param evaluationsByPolicy total {@code evaluate} calls grouped by {@link
     *     GuardrailPolicy#name()}
     * @param decisionsByAction decisions grouped by {@link GuardrailDecision.Action} name (plus the
     *     synthetic {@code ERROR} bucket for policy exceptions)
     * @param failuresByPolicy uncaught exceptions thrown by each policy
     * @param totalDurationMillis cumulative wall-clock time spent inside policy evaluations
     */
    public record GuardrailChainStats(
            Map<String, Long> evaluationsByPolicy,
            Map<String, Long> decisionsByAction,
            Map<String, Long> failuresByPolicy,
            long totalDurationMillis) {}
}
