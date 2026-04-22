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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 */
public class DefaultGuardrailChain implements GuardrailChain {

    private static final Logger log = LoggerFactory.getLogger(DefaultGuardrailChain.class);

    /** Maximum number of policies allowed in a single chain. */
    static final int MAX_POLICIES = 64;

    private final List<GuardrailPolicy> policies;
    private final SecurityEventSink sink;

    /** Creates a chain with no event sink (backward-compatible). */
    public DefaultGuardrailChain(List<GuardrailPolicy> policies) {
        this(policies, null);
    }

    /**
     * Creates a chain that emits {@link SecurityEvent}s to the given sink after each policy
     * evaluation.
     *
     * @param policies the ordered list of guardrail policies
     * @param sink the event sink (may be {@code null} to disable event emission)
     * @throws IllegalArgumentException if the number of policies exceeds {@link #MAX_POLICIES}
     */
    public DefaultGuardrailChain(List<GuardrailPolicy> policies, SecurityEventSink sink) {
        if (policies.size() > MAX_POLICIES) {
            throw new IllegalArgumentException(
                    "Too many guardrail policies: "
                            + policies.size()
                            + " exceeds maximum of "
                            + MAX_POLICIES);
        }
        this.policies =
                policies.stream().sorted(Comparator.comparingInt(GuardrailPolicy::order)).toList();
        this.sink = sink;
    }

    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        if (policies.isEmpty()) {
            return Mono.just(GuardrailDecision.allow("no-policy"));
        }
        return evaluateIterative(context);
    }

    /**
     * Iterative chain evaluation using {@link Flux#reduce}. Each policy is evaluated in order; DENY
     * short-circuits via a sentinel wrapper, MODIFY propagates the modified payload to subsequent
     * policies, and WARN/ALLOW continue unchanged.
     */
    private Mono<GuardrailDecision> evaluateIterative(GuardrailContext context) {
        final GuardrailPayload originalPayload = context.payload();

        // Accumulator: carries the current context and an optional short-circuit decision
        record ChainState(GuardrailContext ctx, GuardrailDecision shortCircuit) {}

        return Flux.fromIterable(policies)
                .reduce(
                        new ChainState(context, null),
                        (state, policy) -> {
                            // If a previous policy already short-circuited, skip remaining
                            if (state.shortCircuit() != null) {
                                return state;
                            }
                            // Evaluate policy, guarding against Mono.empty()
                            GuardrailDecision decision =
                                    policy.evaluate(state.ctx())
                                            .switchIfEmpty(
                                                    Mono.just(
                                                            GuardrailDecision.allow("no-decision")))
                                            .block();

                            emitEvent(decision, state.ctx());

                            return switch (decision.action()) {
                                case DENY -> new ChainState(state.ctx(), decision);
                                case MODIFY -> {
                                    GuardrailContext modified =
                                            new GuardrailContext(
                                                    state.ctx().phase(),
                                                    state.ctx().agentName(),
                                                    state.ctx().targetName(),
                                                    decision.modifiedPayload(),
                                                    Map.copyOf(state.ctx().metadata()));
                                    yield new ChainState(modified, null);
                                }
                                case WARN -> {
                                    log.warn(
                                            "Guardrail warning from {}: {}",
                                            decision.policyName(),
                                            decision.reason());
                                    yield state;
                                }
                                default -> // ALLOW — continue
                                        state;
                            };
                        })
                .map(
                        state -> {
                            // If short-circuited by DENY, return that decision
                            if (state.shortCircuit() != null) {
                                return state.shortCircuit();
                            }
                            // If the payload was modified during evaluation, return MODIFY
                            if (state.ctx().payload() != originalPayload) {
                                return GuardrailDecision.modify(
                                        state.ctx().payload(), "chain-modified", "chain-complete");
                            }
                            return GuardrailDecision.allow("chain-complete");
                        });
    }

    private void emitEvent(GuardrailDecision decision, GuardrailContext context) {
        if (sink != null) {
            SecurityEvent event = SecurityEvent.fromDecision(decision, context);
            sink.record(event);
        }
    }
}
