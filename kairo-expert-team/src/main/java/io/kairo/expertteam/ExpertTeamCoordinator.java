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
package io.kairo.expertteam;

import io.kairo.api.agent.Agent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.team.EvaluationContext;
import io.kairo.api.team.EvaluationStrategy;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamCoordinator;
import io.kairo.api.team.TeamEvent;
import io.kairo.api.team.TeamEventType;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResult;
import io.kairo.api.team.TeamResult.StepOutcome;
import io.kairo.api.team.TeamStatus;
import io.kairo.api.team.TeamStep;
import io.kairo.expertteam.ExpertTeamStateMachine.State;
import io.kairo.expertteam.internal.DefaultGenerator;
import io.kairo.expertteam.internal.DefaultPlanner;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Default plan → generate → evaluate coordinator (ADR-015).
 *
 * <p>Drives a deterministic lifecycle: emit {@code TEAM_STARTED}, run the planner, loop
 * sequentially over every planned step emitting {@code STEP_ASSIGNED} / {@code EVALUATION_STARTED}
 * / {@code EVALUATION_RESULT} / {@code STEP_COMPLETED}, and terminate with exactly one of {@code
 * TEAM_COMPLETED}, {@code TEAM_FAILED}, or {@code TEAM_TIMEOUT}.
 *
 * <p>Failure semantics follow ADR-015 §"Failure semantics":
 *
 * <ul>
 *   <li>Planner: default {@link PlannerFailureMode#FAIL_FAST}; opt-in {@link
 *       PlannerFailureMode#SINGLE_STEP_FALLBACK}.
 *   <li>Evaluator: crashes surface as {@link VerdictOutcome#REVIEW_EXCEEDED}; silent auto-pass is
 *       never permitted.
 *   <li>Review-loop overrun: {@link TeamStatus#FAILED} under MEDIUM / HIGH risk; {@link
 *       TeamStatus#DEGRADED} + warning under LOW risk (explicit, auditable path).
 *   <li>Timeout: enforced via {@link Mono#timeout(Duration)}; in-flight work is cancelled and a
 *       partial {@link TeamResult} with {@link TeamStatus#TIMEOUT} is returned.
 * </ul>
 *
 * <p>v0.10 MVP: sequential step execution only. Honoring {@code
 * TeamResourceConstraint.maxParallelSteps > 1} is a v0.10.1 enhancement; sequential execution still
 * correctly honours the overall {@code teamTimeout}.
 *
 * @since v0.10 (Experimental)
 */
public class ExpertTeamCoordinator implements TeamCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ExpertTeamCoordinator.class);

    @Nullable private final KairoEventBus eventBus;
    private final EvaluationStrategy simpleStrategy;
    @Nullable private final EvaluationStrategy agentStrategy;
    private final DefaultPlanner planner;
    private final DefaultGenerator generator;
    private final ExpertTeamStateMachine stateMachine;

    /**
     * Minimal coordinator with the built-in simple strategy.
     *
     * @param eventBus optional event bus for lifecycle telemetry (may be {@code null})
     */
    public ExpertTeamCoordinator(@Nullable KairoEventBus eventBus) {
        this(eventBus, new SimpleEvaluationStrategy(), null, new DefaultPlanner());
    }

    /**
     * Full-featured coordinator.
     *
     * @param eventBus optional event bus (may be {@code null})
     * @param simpleStrategy deterministic rubric evaluator; never {@code null}
     * @param agentStrategy optional LLM-judge evaluator ({@code null} disables {@link
     *     EvaluatorPreference#AGENT} requests by downgrading to the simple strategy)
     * @param planner deterministic planner used to produce {@link TeamExecutionPlan}; never {@code
     *     null}
     */
    public ExpertTeamCoordinator(
            @Nullable KairoEventBus eventBus,
            EvaluationStrategy simpleStrategy,
            @Nullable EvaluationStrategy agentStrategy,
            DefaultPlanner planner) {
        this.eventBus = eventBus;
        this.simpleStrategy =
                Objects.requireNonNull(simpleStrategy, "simpleStrategy must not be null");
        this.agentStrategy = agentStrategy;
        this.planner = Objects.requireNonNull(planner, "planner must not be null");
        this.generator = new DefaultGenerator();
        this.stateMachine = new ExpertTeamStateMachine();
    }

    @Override
    public Mono<TeamResult> execute(TeamExecutionRequest request, Team team) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(team, "team must not be null");

        Instant started = Instant.now();
        AtomicReference<State> currentState = new AtomicReference<>(State.IDLE);
        List<StepOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Boolean> terminalEmitted = new AtomicReference<>(Boolean.FALSE);

        publish(team, request, TeamEventType.TEAM_STARTED, Map.of("goal", request.goal()));
        transitionTo(currentState, State.PLANNING);

        Mono<TeamResult> pipeline =
                Mono.defer(
                                () ->
                                        runPlanAndSteps(
                                                request,
                                                team,
                                                currentState,
                                                outcomes,
                                                warnings,
                                                started,
                                                terminalEmitted))
                        .timeout(
                                request.config().teamTimeout(),
                                Mono.defer(
                                        () ->
                                                onTimeout(
                                                        request,
                                                        team,
                                                        currentState,
                                                        outcomes,
                                                        warnings,
                                                        started,
                                                        terminalEmitted)));

        return pipeline.onErrorResume(
                ex ->
                        onFailure(
                                request,
                                team,
                                currentState,
                                outcomes,
                                warnings,
                                started,
                                terminalEmitted,
                                ex));
    }

    // ------------------------------------------------------------------ pipeline

    private Mono<TeamResult> runPlanAndSteps(
            TeamExecutionRequest request,
            Team team,
            AtomicReference<State> currentState,
            List<StepOutcome> outcomes,
            List<String> warnings,
            Instant started,
            AtomicReference<Boolean> terminalEmitted) {
        TeamExecutionPlan plan;
        try {
            plan = planner.plan(request, team);
        } catch (RuntimeException primary) {
            if (request.config().plannerFailureMode() == PlannerFailureMode.SINGLE_STEP_FALLBACK) {
                log.warn(
                        "Planner failed ({}); falling back to single-step plan per"
                                + " SINGLE_STEP_FALLBACK",
                        primary.toString());
                warnings.add(
                        "Planner failed; single-step fallback engaged: " + primary.getMessage());
                try {
                    plan = planner.singleStepFallback(request, team);
                } catch (RuntimeException secondary) {
                    return failFromPlanner(
                            request,
                            team,
                            currentState,
                            outcomes,
                            warnings,
                            started,
                            terminalEmitted,
                            secondary);
                }
            } else {
                return failFromPlanner(
                        request,
                        team,
                        currentState,
                        outcomes,
                        warnings,
                        started,
                        terminalEmitted,
                        primary);
            }
        }

        Map<String, Agent> bindings;
        try {
            bindings = planner.bindRoles(team, plan);
        } catch (RuntimeException bindEx) {
            return failFromPlanner(
                    request,
                    team,
                    currentState,
                    outcomes,
                    warnings,
                    started,
                    terminalEmitted,
                    bindEx);
        }

        transitionTo(currentState, State.GENERATING);

        EvaluationStrategy strategy = selectStrategy(request.config());

        Mono<Void> chain = Mono.empty();
        for (TeamStep step : plan.steps()) {
            chain =
                    chain.then(
                            Mono.defer(
                                    () ->
                                            executeStep(
                                                    request,
                                                    team,
                                                    step,
                                                    bindings,
                                                    strategy,
                                                    currentState,
                                                    outcomes,
                                                    warnings)));
        }

        return chain.then(
                Mono.fromSupplier(
                        () ->
                                completeSuccessfully(
                                        request,
                                        team,
                                        currentState,
                                        outcomes,
                                        warnings,
                                        started,
                                        terminalEmitted)));
    }

    private Mono<Void> executeStep(
            TeamExecutionRequest request,
            Team team,
            TeamStep step,
            Map<String, Agent> bindings,
            EvaluationStrategy strategy,
            AtomicReference<State> currentState,
            List<StepOutcome> outcomes,
            List<String> warnings) {

        // Ensure we are in GENERATING before dispatching a step.
        transitionIfNeeded(currentState, State.GENERATING);

        publish(
                team,
                request,
                TeamEventType.STEP_ASSIGNED,
                Map.of("stepId", step.stepId(), "roleId", step.assignedRole().roleId()));

        int maxRounds = request.config().maxFeedbackRounds();
        return attempt(request, team, step, bindings, strategy, 1, new ArrayList<>(), currentState)
                .flatMap(
                        finalOutcome -> {
                            StepAttemptResult result = finalOutcome;
                            EvaluationVerdict verdict = result.verdict();

                            StepOutcome outcome =
                                    new StepOutcome(
                                            step.stepId(),
                                            result.artifact(),
                                            verdict,
                                            result.attempt());
                            outcomes.add(outcome);

                            if (verdict.outcome() == VerdictOutcome.PASS
                                    || verdict.outcome() == VerdictOutcome.AUTO_PASS_WITH_WARNING) {
                                if (verdict.outcome() == VerdictOutcome.AUTO_PASS_WITH_WARNING) {
                                    warnings.add(
                                            "Step '"
                                                    + step.stepId()
                                                    + "' auto-passed with warning: "
                                                    + verdict.feedback());
                                }
                                publish(
                                        team,
                                        request,
                                        TeamEventType.STEP_COMPLETED,
                                        Map.of(
                                                "stepId", step.stepId(),
                                                "attempts", result.attempt()));
                                return Mono.empty();
                            }

                            // REVIEW_EXCEEDED — translate per risk profile.
                            if (verdict.outcome() == VerdictOutcome.REVIEW_EXCEEDED) {
                                RiskProfile risk = request.config().riskProfile();
                                if (risk == RiskProfile.LOW) {
                                    // LOW-risk opt-in: record warning, mark DEGRADED but proceed.
                                    warnings.add(
                                            "Step '"
                                                    + step.stepId()
                                                    + "' exceeded review budget ("
                                                    + maxRounds
                                                    + " rounds); LOW-risk auto-pass with warning");
                                    publish(
                                            team,
                                            request,
                                            TeamEventType.STEP_COMPLETED,
                                            Map.of(
                                                    "stepId",
                                                    step.stepId(),
                                                    "attempts",
                                                    result.attempt(),
                                                    "degraded",
                                                    Boolean.TRUE));
                                    return Mono.empty();
                                }
                                // MEDIUM / HIGH: hard failure.
                                return Mono.error(
                                        new StepReviewExceededException(
                                                step.stepId(), result.attempt(), verdict));
                            }

                            // Defensive: any other terminal outcome is treated as failure.
                            return Mono.error(
                                    new IllegalStateException(
                                            "Unexpected terminal verdict for step '"
                                                    + step.stepId()
                                                    + "': "
                                                    + verdict.outcome()));
                        });
    }

    private Mono<StepAttemptResult> attempt(
            TeamExecutionRequest request,
            Team team,
            TeamStep step,
            Map<String, Agent> bindings,
            EvaluationStrategy strategy,
            int attemptNumber,
            List<EvaluationVerdict> priorVerdicts,
            AtomicReference<State> currentState) {

        int maxRounds = request.config().maxFeedbackRounds();

        transitionIfNeeded(currentState, State.GENERATING);

        return generator
                .generate(step, bindings, request.goal(), attemptNumber, priorVerdicts)
                .flatMap(
                        artifact -> {
                            transitionIfNeeded(currentState, State.EVALUATING);
                            publish(
                                    team,
                                    request,
                                    TeamEventType.EVALUATION_STARTED,
                                    Map.of("stepId", step.stepId(), "attempt", attemptNumber));
                            EvaluationContext ctx =
                                    new EvaluationContext(
                                            step,
                                            artifact,
                                            attemptNumber,
                                            List.copyOf(priorVerdicts),
                                            request.config());
                            return strategy.evaluate(ctx)
                                    .defaultIfEmpty(nullVerdict())
                                    .map(
                                            verdict -> {
                                                publish(
                                                        team,
                                                        request,
                                                        TeamEventType.EVALUATION_RESULT,
                                                        Map.of(
                                                                "stepId", step.stepId(),
                                                                "attempt", attemptNumber,
                                                                "outcome", verdict.outcome().name(),
                                                                "score", verdict.score()));
                                                return new StepAttemptResult(
                                                        artifact, verdict, attemptNumber);
                                            });
                        })
                .flatMap(
                        result -> {
                            EvaluationVerdict verdict = result.verdict();
                            if (verdict.outcome() != VerdictOutcome.REVISE) {
                                return Mono.just(result);
                            }
                            if (attemptNumber >= maxRounds) {
                                // Review budget exhausted: synthesise a REVIEW_EXCEEDED verdict.
                                EvaluationVerdict exceeded =
                                        new EvaluationVerdict(
                                                VerdictOutcome.REVIEW_EXCEEDED,
                                                verdict.score(),
                                                "Review budget exhausted after "
                                                        + attemptNumber
                                                        + " attempt(s); last feedback: "
                                                        + verdict.feedback(),
                                                verdict.suggestions(),
                                                Instant.now());
                                return Mono.just(
                                        new StepAttemptResult(
                                                result.artifact(), exceeded, attemptNumber));
                            }
                            List<EvaluationVerdict> nextPrior = new ArrayList<>(priorVerdicts);
                            nextPrior.add(verdict);
                            transitionIfNeeded(currentState, State.GENERATING);
                            return attempt(
                                    request,
                                    team,
                                    step,
                                    bindings,
                                    strategy,
                                    attemptNumber + 1,
                                    nextPrior,
                                    currentState);
                        });
    }

    // ------------------------------------------------------------------ terminal paths

    private TeamResult completeSuccessfully(
            TeamExecutionRequest request,
            Team team,
            AtomicReference<State> currentState,
            List<StepOutcome> outcomes,
            List<String> warnings,
            Instant started,
            AtomicReference<Boolean> terminalEmitted) {
        boolean degraded = !warnings.isEmpty();
        State terminal = degraded ? State.DEGRADED : State.COMPLETED;
        transitionTo(currentState, terminal);

        TeamStatus status = degraded ? TeamStatus.DEGRADED : TeamStatus.COMPLETED;
        String finalOutput = assembleFinalOutput(outcomes);
        TeamResult result =
                TeamResult.of(
                        request.requestId(),
                        status,
                        List.copyOf(outcomes),
                        finalOutput,
                        Duration.between(started, Instant.now()),
                        List.copyOf(warnings));

        emitTerminalOnce(team, request, TeamEventType.TEAM_COMPLETED, terminalEmitted);
        return result;
    }

    private Mono<TeamResult> onTimeout(
            TeamExecutionRequest request,
            Team team,
            AtomicReference<State> currentState,
            List<StepOutcome> outcomes,
            List<String> warnings,
            Instant started,
            AtomicReference<Boolean> terminalEmitted) {
        return Mono.fromSupplier(
                () -> {
                    warnings.add(
                            "Team execution timed out after "
                                    + request.config().teamTimeout()
                                    + "; partial result returned");
                    transitionTo(currentState, State.TIMEOUT);
                    emitTerminalOnce(team, request, TeamEventType.TEAM_TIMEOUT, terminalEmitted);
                    return TeamResult.withoutOutput(
                            request.requestId(),
                            TeamStatus.TIMEOUT,
                            List.copyOf(outcomes),
                            Duration.between(started, Instant.now()),
                            List.copyOf(warnings));
                });
    }

    private Mono<TeamResult> onFailure(
            TeamExecutionRequest request,
            Team team,
            AtomicReference<State> currentState,
            List<StepOutcome> outcomes,
            List<String> warnings,
            Instant started,
            AtomicReference<Boolean> terminalEmitted,
            Throwable ex) {
        if (ex instanceof TimeoutException) {
            // Reactor wraps timeouts this way; already handled in-band.
            return onTimeout(
                    request, team, currentState, outcomes, warnings, started, terminalEmitted);
        }
        warnings.add("Team failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        transitionTo(currentState, State.FAILED);
        emitTerminalOnce(team, request, TeamEventType.TEAM_FAILED, terminalEmitted);
        return Mono.just(
                TeamResult.withoutOutput(
                        request.requestId(),
                        TeamStatus.FAILED,
                        List.copyOf(outcomes),
                        Duration.between(started, Instant.now()),
                        List.copyOf(warnings)));
    }

    private Mono<TeamResult> failFromPlanner(
            TeamExecutionRequest request,
            Team team,
            AtomicReference<State> currentState,
            List<StepOutcome> outcomes,
            List<String> warnings,
            Instant started,
            AtomicReference<Boolean> terminalEmitted,
            Throwable ex) {
        warnings.add("Planner failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        transitionTo(currentState, State.FAILED);
        emitTerminalOnce(team, request, TeamEventType.TEAM_FAILED, terminalEmitted);
        return Mono.just(
                TeamResult.withoutOutput(
                        request.requestId(),
                        TeamStatus.FAILED,
                        List.copyOf(outcomes),
                        Duration.between(started, Instant.now()),
                        List.copyOf(warnings)));
    }

    // ------------------------------------------------------------------ helpers

    private EvaluationStrategy selectStrategy(TeamConfig config) {
        EvaluatorPreference preference = config.evaluatorPreference();
        return switch (preference) {
            case SIMPLE -> simpleStrategy;
            case AGENT -> {
                if (agentStrategy != null) {
                    yield agentStrategy;
                }
                log.info(
                        "EvaluatorPreference.AGENT requested but no agent strategy wired;"
                                + " falling back to simple evaluator");
                yield simpleStrategy;
            }
            case AUTO -> {
                // Heuristic: HIGH risk prefers the deterministic evaluator; LOW/MEDIUM use
                // the agent strategy when available (its opt-in nature is enforced by the
                // coordinator wiring: no agent strategy wired ⇒ always simple).
                if (agentStrategy != null && config.riskProfile() != RiskProfile.HIGH) {
                    yield agentStrategy;
                }
                yield simpleStrategy;
            }
        };
    }

    private void transitionTo(AtomicReference<State> currentState, State to) {
        State from = currentState.get();
        stateMachine.transition(from, to);
        currentState.set(to);
    }

    private void transitionIfNeeded(AtomicReference<State> currentState, State to) {
        State from = currentState.get();
        if (from == to) {
            return;
        }
        if (!stateMachine.canTransition(from, to)) {
            // When the coordinator is already past the requested state (e.g. EVALUATING->
            // GENERATING during a revise loop) the allowed-map covers it; anything else is a
            // real bug and should surface.
            throw new IllegalStateException("Invalid state transition: " + from + " -> " + to);
        }
        currentState.set(to);
    }

    private String assembleFinalOutput(List<StepOutcome> outcomes) {
        if (outcomes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (StepOutcome o : outcomes) {
            if (sb.length() > 0) {
                sb.append("\n\n---\n\n");
            }
            sb.append("[").append(o.stepId()).append("]\n").append(o.output());
        }
        return sb.toString();
    }

    private void publish(
            Team team,
            TeamExecutionRequest request,
            TeamEventType type,
            Map<String, Object> attributes) {
        if (eventBus == null) {
            return;
        }
        Map<String, Object> attrs = attributes == null ? Map.of() : new HashMap<>(attributes);
        TeamEvent event =
                new TeamEvent(
                        type, team.name(), request.requestId(), Instant.now(), Map.copyOf(attrs));
        try {
            eventBus.publish(event.toKairoEvent());
        } catch (RuntimeException ex) {
            log.debug("KairoEventBus publish failed for team event {}: {}", type, ex.toString());
        }
    }

    private void emitTerminalOnce(
            Team team,
            TeamExecutionRequest request,
            TeamEventType type,
            AtomicReference<Boolean> terminalEmitted) {
        if (terminalEmitted.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
            publish(team, request, type, Map.of());
        }
    }

    private static EvaluationVerdict nullVerdict() {
        return new EvaluationVerdict(
                VerdictOutcome.REVIEW_EXCEEDED,
                0.0,
                "Evaluator produced an empty Mono (contract violation)",
                List.of(),
                Instant.now());
    }

    // ------------------------------------------------------------------ internal types

    /** Result of a single generate + evaluate pass. */
    private record StepAttemptResult(String artifact, EvaluationVerdict verdict, int attempt) {
        StepAttemptResult {
            Objects.requireNonNull(artifact, "artifact must not be null");
            Objects.requireNonNull(verdict, "verdict must not be null");
        }
    }

    /** Propagated when MEDIUM/HIGH risk review budget is exhausted. */
    private static final class StepReviewExceededException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        StepReviewExceededException(String stepId, int attempts, EvaluationVerdict verdict) {
            super(
                    "Step '"
                            + stepId
                            + "' exceeded review budget after "
                            + attempts
                            + " attempt(s); final outcome="
                            + verdict.outcome()
                            + (verdict.feedback().isBlank()
                                    ? ""
                                    : "; feedback=" + verdict.feedback()));
        }
    }

    /** Exposed for tests — reflects the coordinator's policy view on evaluator selection. */
    Optional<EvaluationStrategy> agentStrategy() {
        return Optional.ofNullable(agentStrategy);
    }
}
