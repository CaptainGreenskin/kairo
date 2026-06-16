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
package io.kairo.multiagent.orchestration;

import io.kairo.api.agent.Agent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluationContext;
import io.kairo.api.team.EvaluationStrategy;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.MessageBus;
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
import io.kairo.core.agent.ToolCallSink;
import io.kairo.multiagent.orchestration.ExpertTeamStateMachine.State;
import io.kairo.multiagent.orchestration.internal.DagExecutor;
import io.kairo.multiagent.orchestration.internal.DefaultGenerator;
import io.kairo.multiagent.orchestration.internal.DefaultPlanner;
import io.kairo.multiagent.subagent.ExpertProfile;
import io.kairo.multiagent.subagent.ExpertRoleRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    private final DagExecutor dagExecutor = new DagExecutor();
    private final ExpertTeamStateMachine stateMachine;
    @Nullable private final ExpertRoleRegistry roleRegistry;
    @Nullable private final MessageBus messageBus;
    @Nullable private final ArchitectArbitrator arbitrator;
    @Nullable private final SynthesizerStep synthesizer;
    @Nullable private volatile PlanVerificationStrategy planVerifier;
    private final AtomicLong eventSeq = new AtomicLong(0);

    /**
     * Holds pending plans for teams executing in planOnly mode. Key: teamId (team.name()), Value:
     * the execution context needed to resume.
     */
    private final ConcurrentHashMap<String, PendingPlanContext> pendingPlans =
            new ConcurrentHashMap<>();

    /**
     * Minimal coordinator with the built-in simple strategy.
     *
     * @param eventBus optional event bus for lifecycle telemetry (may be {@code null})
     */
    public ExpertTeamCoordinator(@Nullable KairoEventBus eventBus) {
        this(eventBus, new SimpleEvaluationStrategy(), null, new DefaultPlanner(), null, null);
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
        this(eventBus, simpleStrategy, agentStrategy, planner, null, null);
    }

    /**
     * Full-featured coordinator with role registry for self-correction escalation.
     *
     * @param eventBus optional event bus (may be {@code null})
     * @param simpleStrategy deterministic rubric evaluator; never {@code null}
     * @param agentStrategy optional LLM-judge evaluator ({@code null} disables {@link
     *     EvaluatorPreference#AGENT} requests by downgrading to the simple strategy)
     * @param planner deterministic planner used to produce {@link TeamExecutionPlan}; never {@code
     *     null}
     * @param roleRegistry optional expert role registry for model escalation; {@code null} disables
     *     senior-model retry
     */
    public ExpertTeamCoordinator(
            @Nullable KairoEventBus eventBus,
            EvaluationStrategy simpleStrategy,
            @Nullable EvaluationStrategy agentStrategy,
            DefaultPlanner planner,
            @Nullable ExpertRoleRegistry roleRegistry) {
        this(eventBus, simpleStrategy, agentStrategy, planner, roleRegistry, null);
    }

    /**
     * Full-featured coordinator with role registry and message bus for observable feedback loops.
     *
     * @param eventBus optional event bus (may be {@code null})
     * @param simpleStrategy deterministic rubric evaluator; never {@code null}
     * @param agentStrategy optional LLM-judge evaluator ({@code null} disables {@link
     *     EvaluatorPreference#AGENT} requests by downgrading to the simple strategy)
     * @param planner deterministic planner used to produce {@link TeamExecutionPlan}; never {@code
     *     null}
     * @param roleRegistry optional expert role registry for model escalation; {@code null} disables
     *     senior-model retry
     * @param messageBus optional message bus for inter-agent feedback routing; {@code null} skips
     *     feedback message delivery but events are still emitted
     */
    public ExpertTeamCoordinator(
            @Nullable KairoEventBus eventBus,
            EvaluationStrategy simpleStrategy,
            @Nullable EvaluationStrategy agentStrategy,
            DefaultPlanner planner,
            @Nullable ExpertRoleRegistry roleRegistry,
            @Nullable MessageBus messageBus) {
        this(eventBus, simpleStrategy, agentStrategy, planner, roleRegistry, messageBus, null);
    }

    /**
     * Full-featured coordinator with role registry, message bus, and architect arbitrator.
     *
     * @param eventBus optional event bus (may be {@code null})
     * @param simpleStrategy deterministic rubric evaluator; never {@code null}
     * @param agentStrategy optional LLM-judge evaluator ({@code null} disables {@link
     *     EvaluatorPreference#AGENT} requests by downgrading to the simple strategy)
     * @param planner deterministic planner used to produce {@link TeamExecutionPlan}; never {@code
     *     null}
     * @param roleRegistry optional expert role registry for model escalation; {@code null} disables
     *     senior-model retry
     * @param messageBus optional message bus for inter-agent feedback routing; {@code null} skips
     *     feedback message delivery but events are still emitted
     * @param arbitrator optional architect arbitrator for resolving exhausted feedback loops;
     *     {@code null} disables architect escalation
     */
    public ExpertTeamCoordinator(
            @Nullable KairoEventBus eventBus,
            EvaluationStrategy simpleStrategy,
            @Nullable EvaluationStrategy agentStrategy,
            DefaultPlanner planner,
            @Nullable ExpertRoleRegistry roleRegistry,
            @Nullable MessageBus messageBus,
            @Nullable ArchitectArbitrator arbitrator) {
        this(
                eventBus,
                simpleStrategy,
                agentStrategy,
                planner,
                roleRegistry,
                messageBus,
                arbitrator,
                null);
    }

    /**
     * Full-featured coordinator with role registry, message bus, architect arbitrator, and
     * synthesizer.
     *
     * @param eventBus optional event bus (may be {@code null})
     * @param simpleStrategy deterministic rubric evaluator; never {@code null}
     * @param agentStrategy optional LLM-judge evaluator ({@code null} disables {@link
     *     EvaluatorPreference#AGENT} requests by downgrading to the simple strategy)
     * @param planner deterministic planner used to produce {@link TeamExecutionPlan}; never {@code
     *     null}
     * @param roleRegistry optional expert role registry for model escalation; {@code null} disables
     *     senior-model retry
     * @param messageBus optional message bus for inter-agent feedback routing; {@code null} skips
     *     feedback message delivery but events are still emitted
     * @param arbitrator optional architect arbitrator for resolving exhausted feedback loops;
     *     {@code null} disables architect escalation
     * @param synthesizer optional synthesizer for producing a final integrated output; {@code null}
     *     falls back to simple concatenation of step outputs
     */
    public ExpertTeamCoordinator(
            @Nullable KairoEventBus eventBus,
            EvaluationStrategy simpleStrategy,
            @Nullable EvaluationStrategy agentStrategy,
            DefaultPlanner planner,
            @Nullable ExpertRoleRegistry roleRegistry,
            @Nullable MessageBus messageBus,
            @Nullable ArchitectArbitrator arbitrator,
            @Nullable SynthesizerStep synthesizer) {
        this.eventBus = eventBus;
        this.simpleStrategy =
                Objects.requireNonNull(simpleStrategy, "simpleStrategy must not be null");
        this.agentStrategy = agentStrategy;
        this.planner = Objects.requireNonNull(planner, "planner must not be null");
        this.generator = new DefaultGenerator();
        this.stateMachine = new ExpertTeamStateMachine();
        this.roleRegistry = roleRegistry;
        this.messageBus = messageBus;
        this.arbitrator = arbitrator;
        this.synthesizer = synthesizer;
    }

    /**
     * Set an optional plan verifier that runs after all steps complete. When set, the verifier
     * checks structural correctness and appends warnings/issues to the result.
     *
     * @param verifier the verification strategy, or null to disable
     * @return this coordinator for chaining
     */
    public ExpertTeamCoordinator withPlanVerifier(@Nullable PlanVerificationStrategy verifier) {
        this.planVerifier = verifier;
        return this;
    }

    @Override
    public Mono<TeamResult> execute(TeamExecutionRequest request, Team team) {
        return execute(request, team, false);
    }

    /**
     * Execute a team request. When {@code planOnly} is true, the coordinator runs only the planner
     * step, emits a {@link TeamEventType#PLAN_READY} event with the DAG, and stops without
     * executing steps. Call {@link #confirmAndExecute(String)} to resume.
     *
     * @param request the execution request
     * @param team the team of agents
     * @param planOnly if true, stop after planning and emit PLAN_READY
     * @return the team result (immediate if planOnly; full result if not)
     */
    public Mono<TeamResult> execute(TeamExecutionRequest request, Team team, boolean planOnly) {
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
                                                terminalEmitted,
                                                planOnly))
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

        // Planning runs synchronously at the head of runPlanAndSteps and, when an LLM planning
        // agent is wired, blocks on the model call. Offload the whole pipeline to boundedElastic
        // so that block() is legal regardless of the caller's thread (reactor-netty/parallel),
        // and so the team timeout can cancel a hung planning call cleanly.
        return pipeline.subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(
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

    /**
     * Resume execution of a previously planned team (planOnly mode). Retrieves the pending plan
     * context and runs the DAG steps.
     *
     * @param teamId the team ID returned from the planOnly execution
     * @return the team result after full execution
     */
    public Mono<TeamResult> confirmAndExecute(String teamId) {
        Objects.requireNonNull(teamId, "teamId must not be null");
        PendingPlanContext ctx = pendingPlans.remove(teamId);
        if (ctx == null) {
            return Mono.error(
                    new IllegalStateException("No pending plan found for teamId '" + teamId + "'"));
        }

        Instant started = Instant.now();
        AtomicReference<State> currentState = new AtomicReference<>(State.PLAN_READY);
        List<StepOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Boolean> terminalEmitted = new AtomicReference<>(Boolean.FALSE);

        transitionTo(currentState, State.GENERATING);

        EvaluationStrategy strategy = selectStrategy(ctx.request().config());

        log.debug(" confirmAndExecute: {} steps to run", ctx.plan().steps().size());
        // Schedule by the plan DAG: dependency-driven, independent steps run in parallel within a
        // layer (bounded). Replaces the old strictly-sequential array-order chain.
        Mono<TeamResult> pipeline =
                dagExecutor
                        .execute(
                                ctx.plan().steps(),
                                step ->
                                        executeStep(
                                                        ctx.request(),
                                                        ctx.team(),
                                                        step,
                                                        ctx.bindings(),
                                                        strategy,
                                                        currentState,
                                                        outcomes,
                                                        warnings)
                                                .thenReturn(step.stepId())
                                                .onErrorResume(
                                                        e -> {
                                                            log.warn(
                                                                    "Step '{}' failed: {}",
                                                                    step.stepId(),
                                                                    e.getMessage());
                                                            warnings.add(
                                                                    "Step '"
                                                                            + step.description()
                                                                            + "' failed: "
                                                                            + e.getMessage());
                                                            outcomes.add(
                                                                    new StepOutcome(
                                                                            step.stepId(),
                                                                            "",
                                                                            new EvaluationVerdict(
                                                                                    VerdictOutcome
                                                                                            .REVIEW_EXCEEDED,
                                                                                    0.0,
                                                                                    "Step failed: "
                                                                                            + e
                                                                                                    .getMessage(),
                                                                                    java.util.List
                                                                                            .of(),
                                                                                    java.time
                                                                                            .Instant
                                                                                            .now()),
                                                                            0));
                                                            return Mono.just(step.stepId());
                                                        }))
                        .then(
                                Mono.defer(
                                        () ->
                                                completeSuccessfully(
                                                        ctx.request(),
                                                        ctx.team(),
                                                        currentState,
                                                        outcomes,
                                                        warnings,
                                                        started,
                                                        terminalEmitted,
                                                        ctx.plan())));

        return pipeline.timeout(
                        ctx.request().config().teamTimeout(),
                        Mono.defer(
                                () ->
                                        onTimeout(
                                                ctx.request(),
                                                ctx.team(),
                                                currentState,
                                                outcomes,
                                                warnings,
                                                started,
                                                terminalEmitted)))
                .onErrorResume(
                        ex ->
                                onFailure(
                                        ctx.request(),
                                        ctx.team(),
                                        currentState,
                                        outcomes,
                                        warnings,
                                        started,
                                        terminalEmitted,
                                        ex));
    }

    /** Check if a pending plan exists for the given team ID. */
    public boolean hasPendingPlan(String teamId) {
        return pendingPlans.containsKey(teamId);
    }

    // ------------------------------------------------------------------ pipeline

    private Mono<TeamResult> runPlanAndSteps(
            TeamExecutionRequest request,
            Team team,
            AtomicReference<State> currentState,
            List<StepOutcome> outcomes,
            List<String> warnings,
            Instant started,
            AtomicReference<Boolean> terminalEmitted,
            boolean planOnly) {
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

        // ── planOnly: emit PLAN_READY, store context, return immediately ──
        if (planOnly) {
            transitionTo(currentState, State.PLAN_READY);

            // Build DAG JSON payload for the event
            List<Map<String, Object>> dagPayload = new ArrayList<>();
            for (TeamStep step : plan.steps()) {
                Map<String, Object> stepMap = new LinkedHashMap<>();
                stepMap.put("stepId", step.stepId());
                stepMap.put("roleId", step.assignedRole().roleId());
                stepMap.put("roleName", step.assignedRole().roleName());
                stepMap.put("instruction", step.description());
                stepMap.put("dependsOn", step.dependsOn());
                stepMap.put("stepIndex", step.stepIndex());
                dagPayload.add(stepMap);
            }

            Map<String, Object> planReadyAttrs = new LinkedHashMap<>();
            planReadyAttrs.put("mode", "dag");
            planReadyAttrs.put("planId", plan.planId());
            planReadyAttrs.put("goal", request.goal());
            planReadyAttrs.put("steps", dagPayload);
            planReadyAttrs.put("totalSteps", plan.totalSteps());
            publish(team, request, TeamEventType.PLAN_READY, planReadyAttrs);

            // Store the pending plan so confirmAndExecute can resume
            pendingPlans.put(team.name(), new PendingPlanContext(request, team, plan, bindings));

            // Return a "plan-ready" result indicating the plan is awaiting confirmation
            return Mono.just(
                    TeamResult.withoutOutput(
                            request.requestId(),
                            TeamStatus.COMPLETED,
                            List.of(),
                            Duration.between(started, Instant.now()),
                            List.of("Plan generated; awaiting confirmation")));
        }

        transitionTo(currentState, State.GENERATING);

        EvaluationStrategy strategy = selectStrategy(request.config());

        TeamExecutionPlan finalPlan = plan;
        // Dependency-driven DAG scheduling (parallel within a layer), replacing sequential order.
        return dagExecutor
                .execute(
                        plan.steps(),
                        step ->
                                executeStep(
                                                request,
                                                team,
                                                step,
                                                bindings,
                                                strategy,
                                                currentState,
                                                outcomes,
                                                warnings)
                                        .thenReturn(step.stepId())
                                        .onErrorResume(
                                                ex -> {
                                                    log.warn(
                                                            "Expert step '{}' failed, skipping: {}",
                                                            step.stepId(),
                                                            ex.getMessage());
                                                    warnings.add(
                                                            "Step '"
                                                                    + step.stepId()
                                                                    + "' failed: "
                                                                    + ex.getMessage());
                                                    outcomes.add(
                                                            new StepOutcome(
                                                                    step.stepId(),
                                                                    "",
                                                                    new EvaluationVerdict(
                                                                            VerdictOutcome
                                                                                    .REVIEW_EXCEEDED,
                                                                            0.0,
                                                                            "Step failed: "
                                                                                    + ex
                                                                                            .getMessage(),
                                                                            java.util.List.of(),
                                                                            java.time.Instant
                                                                                    .now()),
                                                                    0));
                                                    publish(
                                                            team,
                                                            request,
                                                            TeamEventType.STEP_COMPLETED,
                                                            Map.of(
                                                                    "stepId",
                                                                    step.stepId(),
                                                                    "status",
                                                                    "skipped"));
                                                    return Mono.just(step.stepId());
                                                }))
                .then(
                        Mono.defer(
                                () ->
                                        completeSuccessfully(
                                                request,
                                                team,
                                                currentState,
                                                outcomes,
                                                warnings,
                                                started,
                                                terminalEmitted,
                                                finalPlan)));
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
        // Collect the outputs of the steps this one depends on, to feed into the worker's prompt.
        // DagExecutor guarantees a step's whole dependency layer has completed before it starts,
        // so every dependsOn outcome is already present in the shared list.
        List<StepOutcome> upstream;
        synchronized (outcomes) {
            upstream =
                    outcomes.stream().filter(o -> step.dependsOn().contains(o.stepId())).toList();
        }
        return attempt(
                        request,
                        team,
                        step,
                        bindings,
                        strategy,
                        1,
                        new ArrayList<>(),
                        currentState,
                        upstream)
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

                            // REVIEW_EXCEEDED — attempt escalation, then translate per risk
                            // profile.
                            if (verdict.outcome() == VerdictOutcome.REVIEW_EXCEEDED) {
                                return attemptEscalation(
                                                request,
                                                team,
                                                step,
                                                bindings,
                                                strategy,
                                                result,
                                                currentState)
                                        .flatMap(
                                                escalationResult -> {
                                                    if (escalationResult.resolved()) {
                                                        // Senior model succeeded — update outcome
                                                        // and complete step.
                                                        StepOutcome successOutcome =
                                                                new StepOutcome(
                                                                        step.stepId(),
                                                                        escalationResult.artifact(),
                                                                        escalationResult.verdict(),
                                                                        escalationResult.attempt());
                                                        replaceOutcome(
                                                                outcomes,
                                                                step.stepId(),
                                                                successOutcome);
                                                        publish(
                                                                team,
                                                                request,
                                                                TeamEventType.STEP_COMPLETED,
                                                                Map.of(
                                                                        "stepId",
                                                                        step.stepId(),
                                                                        "attempts",
                                                                        escalationResult.attempt(),
                                                                        "escalated",
                                                                        Boolean.TRUE));
                                                        return Mono.<Void>empty();
                                                    }

                                                    // Escalation did not resolve — try
                                                    // architect arbitration if available.
                                                    if (arbitrator != null) {
                                                        StepOutcome failedOutcome =
                                                                new StepOutcome(
                                                                        step.stepId(),
                                                                        escalationResult.artifact(),
                                                                        escalationResult.verdict(),
                                                                        escalationResult.attempt());
                                                        return arbitrator
                                                                .arbitrateFeedbackExhaustion(
                                                                        request.goal(),
                                                                        failedOutcome,
                                                                        escalationResult.verdict(),
                                                                        escalationResult
                                                                                .verdict()
                                                                                .feedback())
                                                                .flatMap(
                                                                        arbResult ->
                                                                                handleArbitrationResult(
                                                                                        arbResult,
                                                                                        step,
                                                                                        bindings,
                                                                                        request,
                                                                                        team,
                                                                                        outcomes,
                                                                                        warnings,
                                                                                        result,
                                                                                        escalationResult,
                                                                                        currentState,
                                                                                        strategy));
                                                    }

                                                    // No arbitrator — apply existing
                                                    // risk semantics.
                                                    RiskProfile risk =
                                                            request.config().riskProfile();
                                                    if (risk == RiskProfile.LOW) {
                                                        warnings.add(
                                                                "Step '"
                                                                        + step.stepId()
                                                                        + "' exceeded review"
                                                                        + " budget ("
                                                                        + maxRounds
                                                                        + " rounds);"
                                                                        + " LOW-risk auto-pass"
                                                                        + " with warning");
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
                                                        return Mono.<Void>empty();
                                                    }
                                                    // MEDIUM / HIGH: hard failure.
                                                    return Mono.error(
                                                            new StepReviewExceededException(
                                                                    step.stepId(),
                                                                    result.attempt(),
                                                                    verdict));
                                                });
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
            AtomicReference<State> currentState,
            List<StepOutcome> upstreamOutcomes) {

        int maxRounds = request.config().maxFeedbackRounds();

        transitionIfNeeded(currentState, State.GENERATING);

        // Stream the worker's real tool calls (read/edit/bash …) as per-step STEP_TOOL_CALL events
        // while it executes — replaces the old single fake "agent.call" event. See ToolCallSink.
        ToolCallSink toolSink =
                (toolName, args, result, isError, ms) -> {
                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("stepId", step.stepId());
                    attrs.put("toolName", toolName);
                    attrs.put("args", args);
                    attrs.put("result", trimForEvent(result));
                    attrs.put("isError", isError);
                    attrs.put("durationMs", ms);
                    publish(team, request, TeamEventType.STEP_TOOL_CALL, attrs);
                };

        return generator
                .generate(
                        step,
                        bindings,
                        request.goal(),
                        attemptNumber,
                        priorVerdicts,
                        toolSink,
                        upstreamOutcomes)
                .doOnNext(
                        artifact ->
                                log.debug(
                                        "generate() emitted artifact ({} chars) for step {}",
                                        artifact != null ? artifact.length() : 0,
                                        step.stepId()))
                .doOnNext(
                        artifact -> {
                            // The expert's final output (summary/report). Tool-level detail is
                            // streamed live via toolSink above; here we surface the artifact.
                            publish(
                                    team,
                                    request,
                                    TeamEventType.STEP_ARTIFACT_CHUNK,
                                    Map.of("stepId", step.stepId(), "chunk", artifact));
                        })
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
                                    .doOnNext(
                                            v ->
                                                    log.debug(
                                                            "evaluate() returned: {} for step {}",
                                                            v.outcome(),
                                                            step.stepId()))
                                    .defaultIfEmpty(nullVerdict())
                                    .map(
                                            verdict -> {
                                                Map<String, Object> evalAttrs = new HashMap<>();
                                                evalAttrs.put("stepId", step.stepId());
                                                evalAttrs.put("attempt", attemptNumber);
                                                evalAttrs.put("outcome", verdict.outcome().name());
                                                evalAttrs.put("score", verdict.score());
                                                evalAttrs.put("round", attemptNumber);
                                                evalAttrs.put("maxRounds", maxRounds);
                                                evalAttrs.put("verdict", verdict.outcome().name());
                                                if (verdict.feedback() != null
                                                        && !verdict.feedback().isBlank()) {
                                                    evalAttrs.put("feedback", verdict.feedback());
                                                }
                                                if (verdict.suggestions() != null
                                                        && !verdict.suggestions().isEmpty()) {
                                                    evalAttrs.put(
                                                            "suggestions", verdict.suggestions());
                                                }
                                                publish(
                                                        team,
                                                        request,
                                                        TeamEventType.EVALUATION_RESULT,
                                                        evalAttrs);
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
                            // Route feedback through MessageBus (fire-and-forget)
                            sendFeedbackViaMessageBus(step, verdict, attemptNumber, maxRounds);
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
                                    currentState,
                                    upstreamOutcomes);
                        });
    }

    // ------------------------------------------------------------------ escalation

    /**
     * Handles the result of architect arbitration after feedback loop exhaustion.
     *
     * <p>If the architect decides REVISED_INSTRUCTION, one final generation attempt is made with
     * the architect's revised instructions. Otherwise (ACCEPT_WITH_CAVEATS), the current output is
     * accepted with a warning added.
     */
    private Mono<Void> handleArbitrationResult(
            ArbitrationResult arbResult,
            TeamStep step,
            Map<String, Agent> bindings,
            TeamExecutionRequest request,
            Team team,
            List<StepOutcome> outcomes,
            List<String> warnings,
            StepAttemptResult originalResult,
            EscalationResult escalationResult,
            AtomicReference<State> currentState,
            EvaluationStrategy strategy) {

        if (arbResult.decision() == ArbitrationDecision.REVISED_INSTRUCTION) {
            // One more attempt with architect's revised instruction
            int finalAttempt = escalationResult.attempt() + 1;
            List<EvaluationVerdict> priorVerdicts = List.of(escalationResult.verdict());

            transitionIfNeeded(currentState, State.GENERATING);

            return generator
                    .generate(step, bindings, request.goal(), finalAttempt, priorVerdicts)
                    .flatMap(
                            artifact -> {
                                transitionIfNeeded(currentState, State.EVALUATING);
                                EvaluationContext ctx =
                                        new EvaluationContext(
                                                step,
                                                artifact,
                                                finalAttempt,
                                                priorVerdicts,
                                                request.config());
                                return strategy.evaluate(ctx)
                                        .defaultIfEmpty(nullVerdict())
                                        .flatMap(
                                                finalVerdict -> {
                                                    StepOutcome finalOutcome =
                                                            new StepOutcome(
                                                                    step.stepId(),
                                                                    artifact,
                                                                    finalVerdict,
                                                                    finalAttempt);
                                                    replaceOutcome(
                                                            outcomes, step.stepId(), finalOutcome);
                                                    warnings.add(
                                                            "Step '"
                                                                    + step.stepId()
                                                                    + "' resolved via architect"
                                                                    + " arbitration"
                                                                    + " (REVISED_INSTRUCTION)");
                                                    publish(
                                                            team,
                                                            request,
                                                            TeamEventType.STEP_COMPLETED,
                                                            Map.of(
                                                                    "stepId",
                                                                    step.stepId(),
                                                                    "attempts",
                                                                    finalAttempt,
                                                                    "arbitrated",
                                                                    Boolean.TRUE));
                                                    return Mono.<Void>empty();
                                                });
                            })
                    .onErrorResume(
                            ex -> {
                                // If the final attempt fails, accept with caveats
                                log.warn(
                                        "Architect REVISED_INSTRUCTION final attempt failed for"
                                                + " step '{}': {}",
                                        step.stepId(),
                                        ex.toString());
                                warnings.add(
                                        "Step '"
                                                + step.stepId()
                                                + "' architect-revised attempt failed;"
                                                + " accepting prior output with caveats");
                                publish(
                                        team,
                                        request,
                                        TeamEventType.STEP_COMPLETED,
                                        Map.of(
                                                "stepId",
                                                step.stepId(),
                                                "attempts",
                                                originalResult.attempt(),
                                                "degraded",
                                                Boolean.TRUE,
                                                "arbitrated",
                                                Boolean.TRUE));
                                return Mono.empty();
                            });
        }

        // ACCEPT_WITH_CAVEATS: accept current output, add warning
        warnings.add(
                "Step '"
                        + step.stepId()
                        + "' accepted via architect arbitration: "
                        + arbResult.rationale());
        publish(
                team,
                request,
                TeamEventType.STEP_COMPLETED,
                Map.of(
                        "stepId",
                        step.stepId(),
                        "attempts",
                        originalResult.attempt(),
                        "degraded",
                        Boolean.TRUE,
                        "arbitrated",
                        Boolean.TRUE));
        return Mono.empty();
    }

    /**
     * Sends evaluation feedback to the step's agent via MessageBus (fire-and-forget).
     *
     * <p>If {@link #messageBus} is {@code null}, this method is a no-op.
     */
    private void sendFeedbackViaMessageBus(
            TeamStep step, EvaluationVerdict verdict, int round, int maxRounds) {
        if (messageBus == null) {
            return;
        }
        try {
            String evaluatorId = "evaluator";
            String stepAgentId = step.assignedRole().roleId();
            String feedbackText =
                    String.format(
                            "[REVISE round %d/%d] %s",
                            round, maxRounds, verdict.feedback() != null ? verdict.feedback() : "");
            Msg feedbackMsg =
                    Msg.builder()
                            .role(MsgRole.USER)
                            .addContent(new Content.TextContent(feedbackText))
                            .sourceAgentId(evaluatorId)
                            .metadata("feedbackType", "evaluation_revise")
                            .metadata("round", round)
                            .metadata("maxRounds", maxRounds)
                            .metadata("verdict", verdict.outcome().name())
                            .build();
            // Fire-and-forget: subscribe but don't block
            messageBus.send(evaluatorId, stepAgentId, feedbackMsg).subscribe();
        } catch (RuntimeException ex) {
            log.debug(
                    "MessageBus feedback send failed for step '{}': {}",
                    step.stepId(),
                    ex.toString());
        }
    }

    /**
     * Attempts senior-model escalation when the feedback loop is exhausted.
     *
     * <p>If a model override is configured for the step's role, one bonus attempt is made with the
     * senior model. If the senior model produces a PASS verdict, the escalation is considered
     * resolved. Otherwise (REVISE, error, or no model override), a HANDOFF event is emitted and the
     * escalation is marked unresolved.
     */
    private Mono<EscalationResult> attemptEscalation(
            TeamExecutionRequest request,
            Team team,
            TeamStep step,
            Map<String, Agent> bindings,
            EvaluationStrategy strategy,
            StepAttemptResult failedResult,
            AtomicReference<State> currentState) {

        String roleId = step.assignedRole().roleId();
        String modelOverride = resolveModelOverride(roleId);

        if (modelOverride == null) {
            // No senior model configured — emit HANDOFF immediately.
            log.info(
                    "Step '{}' exhausted review budget; no model override configured, emitting"
                            + " HANDOFF",
                    step.stepId());
            emitHandoff(
                    team, request, step, failedResult.verdict().feedback(), failedResult.attempt());
            return Mono.just(
                    new EscalationResult(
                            false,
                            failedResult.artifact(),
                            failedResult.verdict(),
                            failedResult.attempt()));
        }

        // Senior model bonus round.
        log.info(
                "Step '{}' exhausted review budget; attempting senior model escalation with '{}'",
                step.stepId(),
                modelOverride);

        int seniorAttempt = failedResult.attempt() + 1;
        List<EvaluationVerdict> priorVerdicts = List.of(failedResult.verdict());

        transitionIfNeeded(currentState, State.GENERATING);

        return generator
                .generateWithModelOverride(
                        step, bindings, request.goal(), seniorAttempt, priorVerdicts, modelOverride)
                .flatMap(
                        artifact -> {
                            transitionIfNeeded(currentState, State.EVALUATING);
                            publish(
                                    team,
                                    request,
                                    TeamEventType.EVALUATION_STARTED,
                                    Map.of(
                                            "stepId",
                                            step.stepId(),
                                            "attempt",
                                            seniorAttempt,
                                            "escalated",
                                            Boolean.TRUE));
                            EvaluationContext ctx =
                                    new EvaluationContext(
                                            step,
                                            artifact,
                                            seniorAttempt,
                                            priorVerdicts,
                                            request.config());
                            return strategy.evaluate(ctx)
                                    .defaultIfEmpty(nullVerdict())
                                    .map(
                                            seniorVerdict -> {
                                                publish(
                                                        team,
                                                        request,
                                                        TeamEventType.EVALUATION_RESULT,
                                                        Map.of(
                                                                "stepId", step.stepId(),
                                                                "attempt", seniorAttempt,
                                                                "outcome",
                                                                        seniorVerdict
                                                                                .outcome()
                                                                                .name(),
                                                                "score", seniorVerdict.score(),
                                                                "escalated", Boolean.TRUE));
                                                if (seniorVerdict.outcome() == VerdictOutcome.PASS
                                                        || seniorVerdict.outcome()
                                                                == VerdictOutcome
                                                                        .AUTO_PASS_WITH_WARNING) {
                                                    return new EscalationResult(
                                                            true,
                                                            artifact,
                                                            seniorVerdict,
                                                            seniorAttempt);
                                                }
                                                // Senior model also failed.
                                                emitHandoff(
                                                        team,
                                                        request,
                                                        step,
                                                        seniorVerdict.feedback(),
                                                        seniorAttempt);
                                                return new EscalationResult(
                                                        false,
                                                        artifact,
                                                        seniorVerdict,
                                                        seniorAttempt);
                                            });
                        })
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "Senior model escalation failed for step '{}': {}",
                                    step.stepId(),
                                    ex.toString());
                            emitHandoff(
                                    team,
                                    request,
                                    step,
                                    "Senior model error: " + ex.getMessage(),
                                    seniorAttempt);
                            return Mono.just(
                                    new EscalationResult(
                                            false,
                                            failedResult.artifact(),
                                            failedResult.verdict(),
                                            failedResult.attempt()));
                        });
    }

    @Nullable
    private String resolveModelOverride(String roleId) {
        if (roleRegistry == null) {
            return null;
        }
        return roleRegistry.resolve(roleId).map(ExpertProfile::modelOverride).orElse(null);
    }

    private void emitHandoff(
            Team team,
            TeamExecutionRequest request,
            TeamStep step,
            String feedback,
            int totalAttempts) {
        Map<String, Object> handoffAttrs = new HashMap<>();
        handoffAttrs.put("requiresHuman", Boolean.TRUE);
        handoffAttrs.put("feedback", feedback != null ? feedback : "");
        handoffAttrs.put("stepId", step.stepId());
        handoffAttrs.put("roleId", step.assignedRole().roleId());
        handoffAttrs.put("attempts", totalAttempts);
        handoffAttrs.put("reason", "feedback_loop_exhausted");
        handoffAttrs.put("lastFeedback", feedback != null ? feedback : "");
        publish(team, request, TeamEventType.HANDOFF, handoffAttrs);
    }

    // ------------------------------------------------------------------ terminal paths

    private Mono<TeamResult> completeSuccessfully(
            TeamExecutionRequest request,
            Team team,
            AtomicReference<State> currentState,
            List<StepOutcome> outcomes,
            List<String> warnings,
            Instant started,
            AtomicReference<Boolean> terminalEmitted) {
        return completeSuccessfully(
                request, team, currentState, outcomes, warnings, started, terminalEmitted, null);
    }

    private Mono<TeamResult> completeSuccessfully(
            TeamExecutionRequest request,
            Team team,
            AtomicReference<State> currentState,
            List<StepOutcome> outcomes,
            List<String> warnings,
            Instant started,
            AtomicReference<Boolean> terminalEmitted,
            @Nullable TeamExecutionPlan plan) {
        log.debug(
                "completeSuccessfully ENTERED, outcomes={}, warnings={}",
                outcomes.size(),
                warnings.size());
        boolean degraded = !warnings.isEmpty();
        State terminal = degraded ? State.DEGRADED : State.COMPLETED;
        transitionTo(currentState, terminal);

        TeamStatus status = degraded ? TeamStatus.DEGRADED : TeamStatus.COMPLETED;

        // Under parallel DAG execution outcomes are appended in completion order; restore plan
        // order (by stepIndex) so the final report and TeamResult honour the documented contract.
        List<StepOutcome> orderedOutcomes = orderOutcomesByPlan(outcomes, plan);

        Mono<String> finalOutputMono;
        if (synthesizer != null) {
            finalOutputMono = synthesizer.synthesize(request.goal(), orderedOutcomes);
        } else {
            finalOutputMono = Mono.just(assembleFinalOutput(orderedOutcomes));
        }

        return finalOutputMono.map(
                finalOutput -> {
                    List<String> allWarnings = new ArrayList<>(warnings);

                    if (planVerifier != null && plan != null) {
                        TeamResult preVerifyResult =
                                TeamResult.of(
                                        request.requestId(),
                                        status,
                                        orderedOutcomes,
                                        finalOutput,
                                        Duration.between(started, Instant.now()),
                                        List.copyOf(allWarnings));
                        PlanVerificationVerdict verdict =
                                planVerifier.verify(plan, preVerifyResult, request.goal());
                        if (!verdict.isSuccess()) {
                            allWarnings.add(
                                    "Plan verification "
                                            + verdict.outcome().name()
                                            + ": "
                                            + verdict.reason());
                            for (String issue : verdict.issues()) {
                                allWarnings.add("  - " + issue);
                            }
                        }
                    }

                    TeamResult result =
                            TeamResult.of(
                                    request.requestId(),
                                    status,
                                    orderedOutcomes,
                                    finalOutput,
                                    Duration.between(started, Instant.now()),
                                    List.copyOf(allWarnings));
                    emitTerminalOnce(
                            team,
                            request,
                            TeamEventType.TEAM_COMPLETED,
                            terminalEmitted,
                            finalOutput);
                    return result;
                });
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

    /**
     * Replace the outcome for {@code stepId} (parallel-safe; positional set is unsafe under DAG).
     */
    private static void replaceOutcome(
            List<StepOutcome> outcomes, String stepId, StepOutcome replacement) {
        synchronized (outcomes) {
            for (int i = 0; i < outcomes.size(); i++) {
                if (stepId.equals(outcomes.get(i).stepId())) {
                    outcomes.set(i, replacement);
                    return;
                }
            }
            outcomes.add(replacement);
        }
    }

    /**
     * Return outcomes in plan order (by {@link TeamStep#stepIndex()}). Parallel execution appends
     * them in completion order; the final report and {@link TeamResult} promise plan order.
     */
    private static List<StepOutcome> orderOutcomesByPlan(
            List<StepOutcome> outcomes, @Nullable TeamExecutionPlan plan) {
        List<StepOutcome> copy;
        synchronized (outcomes) {
            copy = new ArrayList<>(outcomes);
        }
        if (plan == null) {
            return copy;
        }
        Map<String, Integer> indexByStep = new java.util.HashMap<>();
        for (TeamStep s : plan.steps()) {
            indexByStep.put(s.stepId(), s.stepIndex());
        }
        copy.sort(
                java.util.Comparator.comparingInt(
                        o -> indexByStep.getOrDefault(o.stepId(), Integer.MAX_VALUE)));
        return copy;
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

    private Map<String, Object> withSeq(Map<String, Object> attrs) {
        Map<String, Object> result = new LinkedHashMap<>(attrs);
        result.put("seq", eventSeq.incrementAndGet());
        return result;
    }

    /** Max chars of a tool result carried in a STEP_TOOL_CALL event (avoid bloating the bus). */
    private static final int MAX_TOOL_RESULT_CHARS = 10_000;

    private static String trimForEvent(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_TOOL_RESULT_CHARS
                ? s
                : s.substring(0, MAX_TOOL_RESULT_CHARS) + "\n…(truncated)";
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
        attrs = withSeq(attrs);
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
        emitTerminalOnce(team, request, type, terminalEmitted, null);
    }

    private void emitTerminalOnce(
            Team team,
            TeamExecutionRequest request,
            TeamEventType type,
            AtomicReference<Boolean> terminalEmitted,
            String finalOutput) {
        if (terminalEmitted.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
            Map<String, Object> attrs =
                    finalOutput != null ? Map.of("finalOutput", finalOutput) : Map.of();
            publish(team, request, type, attrs);
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

    /** Holds the execution context for a team whose plan is pending user confirmation. */
    private record PendingPlanContext(
            TeamExecutionRequest request,
            Team team,
            TeamExecutionPlan plan,
            Map<String, Agent> bindings) {
        PendingPlanContext {
            Objects.requireNonNull(request, "request must not be null");
            Objects.requireNonNull(team, "team must not be null");
            Objects.requireNonNull(plan, "plan must not be null");
            Objects.requireNonNull(bindings, "bindings must not be null");
        }
    }

    /** Result of a single generate + evaluate pass. */
    private record StepAttemptResult(String artifact, EvaluationVerdict verdict, int attempt) {
        StepAttemptResult {
            Objects.requireNonNull(artifact, "artifact must not be null");
            Objects.requireNonNull(verdict, "verdict must not be null");
        }
    }

    /**
     * Outcome of the escalation attempt (senior model retry or immediate handoff).
     *
     * @param resolved {@code true} if the senior model produced a passing verdict
     * @param artifact the artifact produced (may be from the original attempt if unresolved)
     * @param verdict the final verdict from the escalation
     * @param attempt the attempt number at which the escalation concluded
     */
    private record EscalationResult(
            boolean resolved, String artifact, EvaluationVerdict verdict, int attempt) {
        EscalationResult {
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
