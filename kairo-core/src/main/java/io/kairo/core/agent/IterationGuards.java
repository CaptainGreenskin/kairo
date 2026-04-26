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
package io.kairo.core.agent;

import io.kairo.api.agent.CancellationSignal;
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.execution.*;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.message.MsgBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Guard checks and cancellation utilities for the ReAct loop.
 *
 * <p>Evaluates pre-iteration guards (max iterations, token budget, interruption, shutdown) and
 * provides cooperative cancellation helpers for reactive chains.
 *
 * <p>Package-private: not part of the public API.
 */
class IterationGuards {

    private static final Logger log = LoggerFactory.getLogger(IterationGuards.class);

    private final ReActLoopContext ctx;
    private final AtomicBoolean interrupted;
    private final AtomicInteger currentIteration;
    @Nullable private final List<ResourceConstraint> constraints;

    IterationGuards(
            ReActLoopContext ctx, AtomicBoolean interrupted, AtomicInteger currentIteration) {
        this(ctx, interrupted, currentIteration, null);
    }

    IterationGuards(
            ReActLoopContext ctx,
            AtomicBoolean interrupted,
            AtomicInteger currentIteration,
            @Nullable List<ResourceConstraint> constraints) {
        this.ctx = ctx;
        this.interrupted = interrupted;
        this.currentIteration = currentIteration;
        this.constraints = constraints != null ? List.copyOf(constraints) : null;
    }

    /**
     * Evaluate guard conditions before each iteration.
     *
     * @return a Mono that emits a final response Msg if a guard triggered, or completes empty to
     *     proceed
     */
    Mono<Msg> evaluate() {
        // Lifecycle signals — NOT constraints. Always checked first.
        if (interrupted.get()) {
            return Mono.error(
                    new AgentInterruptedException(
                            "Agent '" + ctx.agentName() + "' was interrupted"));
        }

        if (!ctx.shutdownManager().isAcceptingRequests()) {
            log.info("Agent '{}' stopping due to system shutdown", ctx.agentName());
            return Mono.just(buildFinalResponse("Agent stopped due to system shutdown."));
        }

        // Delegate to ResourceConstraint chain if available
        if (constraints != null && !constraints.isEmpty()) {
            return evaluateConstraints();
        }

        // Fallback: existing inline behavior (backward compat)
        if (currentIteration.get() >= ctx.config().maxIterations()) {
            log.warn(
                    "Agent '{}' reached max iterations ({})",
                    ctx.agentName(),
                    ctx.config().maxIterations());
            return Mono.just(
                    buildFinalResponse(
                            "I've reached my maximum iteration limit. Here is what I have so far."));
        }

        long accountedTokens = ctx.tokenBudgetManager().totalAccountedTokens();
        if (accountedTokens >= ctx.config().tokenBudget()) {
            log.warn(
                    "Agent '{}' exceeded token budget ({}/{})",
                    ctx.agentName(),
                    accountedTokens,
                    ctx.config().tokenBudget());
            return Mono.just(
                    buildFinalResponse(
                            "I've reached my token budget. Here is what I have so far."));
        }

        return Mono.empty();
    }

    /**
     * Evaluate the constraint chain reactively. The most severe action wins. EMERGENCY_STOP
     * short-circuits.
     *
     * @return a Mono that emits a final response Msg if a constraint triggered exit, or completes
     *     empty to proceed
     */
    private Mono<Msg> evaluateConstraints() {
        ResourceContext execCtx =
                new ResourceContext(
                        currentIteration.get(),
                        ctx.tokenBudgetManager().totalAccountedTokens(),
                        Duration.ZERO, // elapsed tracking deferred to full ReActLoop integration
                        ctx.agentName(),
                        null);

        record ConstraintAccumulator(ResourceAction worstAction, String worstReason) {
            static ConstraintAccumulator initial() {
                return new ConstraintAccumulator(ResourceAction.ALLOW, null);
            }
        }

        return Flux.fromIterable(constraints)
                .concatMap(
                        constraint ->
                                constraint
                                        .validate(execCtx)
                                        .map(validation -> Map.entry(constraint, validation)))
                .reduce(
                        ConstraintAccumulator.initial(),
                        (acc, entry) -> {
                            // EMERGENCY_STOP already found — skip remaining
                            if (acc.worstAction() == ResourceAction.EMERGENCY_STOP) {
                                return acc;
                            }

                            ResourceValidation validation = entry.getValue();
                            if (!validation.violated()) {
                                return acc;
                            }

                            ResourceConstraint constraint = entry.getKey();
                            ResourceAction action = constraint.onViolation(validation);
                            log.warn(
                                    "Agent '{}' constraint violated: {} (action={})",
                                    ctx.agentName(),
                                    validation.reason(),
                                    action);

                            if (action.ordinal() > acc.worstAction().ordinal()) {
                                return new ConstraintAccumulator(action, validation.reason());
                            }
                            return acc;
                        })
                .flatMap(
                        acc -> {
                            return switch (acc.worstAction()) {
                                case ALLOW, WARN_CONTINUE -> Mono.<Msg>empty();
                                case GRACEFUL_EXIT ->
                                        Mono.just(
                                                buildFinalResponse(
                                                        acc.worstReason() != null
                                                                ? acc.worstReason()
                                                                        + ". Here is what I have so far."
                                                                : "Constraint violated. Here is what I have so far."));
                                case EMERGENCY_STOP ->
                                        Mono.just(
                                                buildFinalResponse(
                                                        acc.worstReason() != null
                                                                ? acc.worstReason()
                                                                        + ". Stopping immediately."
                                                                : "Critical constraint violated. Stopping immediately."));
                            };
                        });
    }

    /**
     * Check if the agent has been interrupted and signal cancellation if so. Inserted at reactive
     * chain boundaries for cooperative cancellation.
     */
    Mono<Void> checkCancelled() {
        if (interrupted.get()) {
            return Mono.error(
                    new AgentInterruptedException(
                            "Agent '"
                                    + ctx.agentName()
                                    + "' interrupted at iteration "
                                    + currentIteration));
        }
        return Mono.empty();
    }

    <T> Mono<T> withCancellationSignal(Mono<T> source) {
        return source.contextWrite(
                ctxView ->
                        ctxView.put(
                                CancellationSignal.CONTEXT_KEY,
                                (CancellationSignal) interrupted::get));
    }

    <T> Flux<T> withCancellationSignal(Flux<T> source) {
        return source.contextWrite(
                ctxView ->
                        ctxView.put(
                                CancellationSignal.CONTEXT_KEY,
                                (CancellationSignal) interrupted::get));
    }

    <T> Mono<T> withCooperativeCancellation(Mono<T> source) {
        return source.takeUntilOther(cancellationTrigger())
                .switchIfEmpty(
                        Mono.defer(
                                () ->
                                        interrupted.get()
                                                ? Mono.error(
                                                        new AgentInterruptedException(
                                                                "Agent '"
                                                                        + ctx.agentName()
                                                                        + "' interrupted at iteration "
                                                                        + currentIteration))
                                                : Mono.empty()));
    }

    <T> Flux<T> withCooperativeCancellation(Flux<T> source) {
        return source.takeUntilOther(cancellationTrigger())
                .concatWith(
                        Flux.defer(
                                () ->
                                        interrupted.get()
                                                ? Flux.error(
                                                        new AgentInterruptedException(
                                                                "Agent '"
                                                                        + ctx.agentName()
                                                                        + "' interrupted at iteration "
                                                                        + currentIteration))
                                                : Flux.empty()));
    }

    /** Build a final text response message. */
    Msg buildFinalResponse(String text) {
        return MsgBuilder.create()
                .role(MsgRole.ASSISTANT)
                .sourceAgentId(ctx.agentId())
                .text(text)
                .build();
    }

    private Mono<Long> cancellationTrigger() {
        if (interrupted.get()) {
            return Mono.just(0L);
        }
        return Flux.interval(Duration.ofMillis(50)).filter(tick -> interrupted.get()).next();
    }
}
