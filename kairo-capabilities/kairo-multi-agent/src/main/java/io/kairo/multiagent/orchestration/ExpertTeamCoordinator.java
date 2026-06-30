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
import io.kairo.api.team.SharedContext;
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
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.agent.DefaultReActAgent;
import io.kairo.core.agent.ToolCallSink;
import io.kairo.multiagent.orchestration.ExpertTeamStateMachine.State;
import io.kairo.multiagent.orchestration.internal.DagExecutor;
import io.kairo.multiagent.orchestration.internal.DefaultGenerator;
import io.kairo.multiagent.orchestration.internal.DefaultPlanner;
import io.kairo.multiagent.orchestration.tool.CachingToolExecutor;
import io.kairo.multiagent.orchestration.tool.ReadFileCache;
import io.kairo.multiagent.subagent.ExpertProfile;
import io.kairo.multiagent.subagent.ExpertRoleRegistry;
import java.lang.reflect.Field;
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
import reactor.core.publisher.Flux;
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
    @Nullable private final ExpertMemoryStore memoryStore;
    @Nullable private final LessonExtractor lessonExtractor;
    @Nullable private volatile WorkspaceContextGatherer contextGatherer;

    /**
     * Level-2 team self-evolution (optional). When set, successful team compositions are recorded
     * at completion and recalled at planning time so the planner reuses what worked for similar
     * tasks. Wired via {@link #setTeamPatternStore} to avoid threading a param through every
     * constructor. Null → L2 disabled (L1 expert memory unaffected).
     */
    @Nullable private volatile TeamPatternStore teamPatternStore;

    @Nullable private volatile PlanVerificationStrategy planVerifier;
    private final AtomicLong eventSeq = new AtomicLong(0);

    /**
     * Holds pending plans for teams executing in planOnly mode. Key: teamId (team.name()), Value:
     * the execution context needed to resume.
     */
    private final ConcurrentHashMap<String, PendingPlanContext> pendingPlans =
            new ConcurrentHashMap<>();

    /**
     * Currently-executing steps, keyed by stepId. Populated for the duration of a step's worker
     * {@code generate(...)} window (see {@link #trackActive}) and cleared when it settles. Enables
     * real-time mid-flight steering: {@link #steer} injects a user directive into the live worker
     * agent's conversation. The coordinator is per-session, so this map only ever holds the active
     * steps of this session's team.
     */
    private final ConcurrentHashMap<String, ActiveStep> activeStepAgents =
            new ConcurrentHashMap<>();

    /** A step actively running its worker agent, with the context needed to emit STEP_STEERED. */
    private record ActiveStep(Agent agent, Team team, TeamExecutionRequest request) {}

    /**
     * Mid-flight user directives accumulated during the current execution (belt-and-suspenders for
     * {@link #steer}): besides the live {@code injectMessages} into the running worker, every steer
     * is recorded here and appended to the goal of every SUBSEQUENT step's worker, so a directive
     * still takes effect even if the step it targeted was already wrapping up. Cleared at the start
     * of each {@link #execute} (a fresh task) so directives never leak across tasks.
     */
    private final java.util.List<String> steerDirectives =
            new java.util.concurrent.CopyOnWriteArrayList<>();

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
                null,
                null,
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
            @Nullable SynthesizerStep synthesizer,
            @Nullable ExpertMemoryStore memoryStore,
            @Nullable LessonExtractor lessonExtractor) {
        this.eventBus = eventBus;
        this.simpleStrategy =
                Objects.requireNonNull(simpleStrategy, "simpleStrategy must not be null");
        this.agentStrategy = agentStrategy;
        this.planner = Objects.requireNonNull(planner, "planner must not be null");
        this.memoryStore = memoryStore;
        this.lessonExtractor = lessonExtractor;
        this.generator = new DefaultGenerator(roleRegistry, null, memoryStore);
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

        // Fresh task: drop any mid-flight directives accumulated by a previous execution.
        steerDirectives.clear();

        Instant started = Instant.now();
        AtomicReference<State> currentState = new AtomicReference<>(State.IDLE);
        List<StepOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Boolean> terminalEmitted = new AtomicReference<>(Boolean.FALSE);

        publish(team, request, TeamEventType.TEAM_STARTED, Map.of("goal", request.goal()));
        transitionTo(currentState, State.PLANNING);

        // Gather the shared workspace context once per execution. A gather failure degrades to an
        // empty context (appendWorkspaceContext becomes a no-op) — the team still runs. Published
        // via Reactor Context so the stateless DefaultGenerator reads it thread-safely per
        // execution rather than through a racy instance field.
        Mono<SharedContext> ctxMono =
                contextGatherer != null
                        ? contextGatherer
                                .gather(request)
                                .onErrorResume(e -> Mono.just(SharedContext.empty()))
                        : Mono.just(SharedContext.empty());

        Mono<TeamResult> pipeline =
                ctxMono.flatMap(
                        sharedContext ->
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
                                                                        terminalEmitted)))
                                        .contextWrite(
                                                ctx ->
                                                        ctx.put(
                                                                DefaultGenerator.SHARED_CONTEXT_KEY,
                                                                sharedContext)));

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

        // ── Install team-scoped ReadFileCache ────────────────────────────────
        ReadFileCache readFileCache = new ReadFileCache();
        Map<Agent, ToolExecutor> cachedOriginals =
                installCachingExecutors(ctx.bindings(), readFileCache);

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
                                                        ctx.plan())))
                        .doFinally(
                                signal -> {
                                    readFileCache.logSummary();
                                    uninstallCachingExecutors(cachedOriginals);
                                });

        // Gather workspace context (same as execute) so the confirmAndExecute path — which is the
        // one experts actually use after plan approval — also publishes SharedContext via Reactor
        // Context for the generator to inject per role scope.
        Mono<SharedContext> ctxMono =
                contextGatherer != null
                        ? contextGatherer
                                .gather(ctx.request())
                                .onErrorResume(e -> Mono.just(SharedContext.empty()))
                        : Mono.just(SharedContext.empty());

        return ctxMono.flatMap(
                sharedContext ->
                        pipeline.timeout(
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
                                                        ex))
                                .contextWrite(
                                        c ->
                                                c.put(
                                                        DefaultGenerator.SHARED_CONTEXT_KEY,
                                                        sharedContext)));
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
            plan = planner.plan(requestForPlanning(request), team);
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

        // ── Install team-scoped ReadFileCache ────────────────────────────────
        ReadFileCache readFileCache = new ReadFileCache();
        Map<Agent, ToolExecutor> cachedOriginals = installCachingExecutors(bindings, readFileCache);

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
                                                finalPlan)))
                .doFinally(
                        signal -> {
                            readFileCache.logSummary();
                            uninstallCachingExecutors(cachedOriginals);
                        });
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
        // Snapshot token count before this step so we can compute the delta after.
        Agent stepAgent = bindings.get(step.assignedRole().roleId());
        long tokensBefore = stepAgent != null ? stepAgent.totalTokensUsed() : 0;
        // Collect the outputs of the steps this one depends on, to feed into the worker's prompt.
        // DagExecutor guarantees a step's whole dependency layer has completed before it starts,
        // so every dependsOn outcome is already present in the shared list.
        List<StepOutcome> upstream;
        synchronized (outcomes) {
            upstream =
                    outcomes.stream().filter(o -> step.dependsOn().contains(o.stepId())).toList();
        }
        java.time.Duration stepTimeout = request.config().stepTimeout();
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
                .timeout(
                        stepTimeout != null ? stepTimeout : java.time.Duration.ofMinutes(10),
                        Mono.defer(
                                () -> {
                                    log.warn(
                                            "Step '{}' exceeded step timeout, marking as completed",
                                            step.stepId());
                                    warnings.add(
                                            "Step '"
                                                    + step.stepId()
                                                    + "' timed out after "
                                                    + (stepTimeout != null
                                                            ? stepTimeout
                                                            : java.time.Duration.ofMinutes(10)));
                                    return Mono.just(
                                            new StepAttemptResult(
                                                    step.stepId() + " (timed out)",
                                                    new EvaluationVerdict(
                                                            EvaluationVerdict.VerdictOutcome.PASS,
                                                            0.5,
                                                            "Step timed out — partial result accepted",
                                                            java.util.List.of(),
                                                            java.time.Instant.now()),
                                                    0));
                                }))
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
                                long stepTokens =
                                        stepAgent != null
                                                ? stepAgent.totalTokensUsed() - tokensBefore
                                                : 0;
                                Map<String, Object> completedAttrs = new LinkedHashMap<>();
                                completedAttrs.put("stepId", step.stepId());
                                completedAttrs.put("attempts", result.attempt());
                                completedAttrs.put("tokensUsed", stepTokens);
                                publish(
                                        team,
                                        request,
                                        TeamEventType.STEP_COMPLETED,
                                        completedAttrs);
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

        return trackActive(
                        step,
                        bindings,
                        team,
                        request,
                        generator.generate(
                                step,
                                bindings,
                                augmentGoal(request.goal()),
                                attemptNumber,
                                priorVerdicts,
                                toolSink,
                                upstreamOutcomes))
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

            return trackActive(
                            step,
                            bindings,
                            team,
                            request,
                            generator.generate(
                                    step,
                                    bindings,
                                    augmentGoal(request.goal()),
                                    finalAttempt,
                                    priorVerdicts))
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

        return trackActive(
                        step,
                        bindings,
                        team,
                        request,
                        generator.generateWithModelOverride(
                                step,
                                bindings,
                                augmentGoal(request.goal()),
                                seniorAttempt,
                                priorVerdicts,
                                modelOverride))
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

        return finalOutputMono.flatMap(
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
                    persistLessonsFireAndForget(request, outcomes, plan);
                    recordTeamPatternFireAndForget(request, plan, true);
                    return Mono.just(result);
                });
    }

    /**
     * Background self-evolution write-back. Fired via subscribe (fire-and-forget) so lesson
     * extraction — an LLM call that may be slow — never blocks or delays the team result. Each role
     * gets a 30s timeout; failures are logged and swallowed.
     */
    /**
     * Group step outcomes by the STABLE roleId (e.g. "expert:coder"), not the per-task requestId.
     * The recall side ({@code DefaultGenerator#appendPriorLessons}) reads lessons by roleId, so
     * writing under requestId meant lessons landed in throwaway UUID namespaces and were NEVER
     * recalled — cross-task self-evolution was silently a no-op. Outcomes whose step has no
     * resolvable role (plan missing / unmapped stepId) are dropped. Visible for testing.
     */
    static java.util.Map<String, List<StepOutcome>> groupOutcomesByRole(
            List<StepOutcome> outcomes, @Nullable TeamExecutionPlan plan) {
        java.util.Map<String, String> stepToRole = new java.util.HashMap<>();
        if (plan != null && plan.steps() != null) {
            for (TeamStep s : plan.steps()) {
                if (s.assignedRole() != null) {
                    stepToRole.put(s.stepId(), s.assignedRole().roleId());
                }
            }
        }
        java.util.Map<String, List<StepOutcome>> byRole = new java.util.LinkedHashMap<>();
        for (StepOutcome o : outcomes) {
            String roleId = stepToRole.get(o.stepId());
            if (roleId == null || roleId.isBlank()) {
                continue;
            }
            byRole.computeIfAbsent(roleId, k -> new java.util.ArrayList<>()).add(o);
        }
        return byRole;
    }

    private void persistLessonsFireAndForget(
            TeamExecutionRequest request,
            List<StepOutcome> outcomes,
            @Nullable TeamExecutionPlan plan) {
        if (memoryStore == null || lessonExtractor == null || outcomes.isEmpty()) {
            return;
        }
        java.util.Map<String, List<StepOutcome>> byRole = groupOutcomesByRole(outcomes, plan);
        if (byRole.isEmpty()) {
            return;
        }
        String goal = request.goal() == null ? "" : request.goal();
        Flux.fromIterable(byRole.entrySet())
                .flatMap(
                        e ->
                                lessonExtractor
                                        .extract(e.getKey(), e.getValue(), goal)
                                        .timeout(Duration.ofSeconds(30))
                                        .flatMap(
                                                lessons ->
                                                        memoryStore
                                                                .recordLessons(
                                                                        e.getKey(),
                                                                        e.getKey(),
                                                                        lessons)
                                                                .then(Mono.<Object>empty()))
                                        .onErrorResume(
                                                ex -> {
                                                    log.warn(
                                                            "Lesson extraction failed for {}: {}",
                                                            e.getKey(),
                                                            ex.getMessage());
                                                    return Mono.empty();
                                                }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v -> {},
                        e -> log.warn("Lesson extraction background error: {}", e.getMessage()),
                        () -> log.debug("Lesson extraction background complete"));
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

    /**
     * Wrap a step's worker {@code generate(...)} so the running worker agent is registered in
     * {@link #activeStepAgents} for the duration of execution, then removed when it settles
     * (success, error, or cancel). This is the window during which {@link #steer} can inject a
     * mid-flight user directive into the live worker.
     */
    private Mono<String> trackActive(
            TeamStep step,
            Map<String, Agent> bindings,
            Team team,
            TeamExecutionRequest request,
            Mono<String> generate) {
        return Mono.defer(
                () -> {
                    Agent agent = bindings.get(step.assignedRole().roleId());
                    if (agent != null) {
                        activeStepAgents.put(step.stepId(), new ActiveStep(agent, team, request));
                    }
                    return generate.doFinally(sig -> activeStepAgents.remove(step.stepId()));
                });
    }

    /**
     * Inject a user directive into one or all currently-executing worker agents (mid-flight
     * steering). The directive is appended to the worker's conversation via {@link
     * Agent#injectMessages} and picked up at its next reasoning iteration without interrupting the
     * current turn.
     *
     * @param stepId target a specific running step; {@code null}/blank → all active steps
     * @param directive the user's instruction; blank → no-op
     * @return {@code true} if at least one active worker received the directive (caller can fall
     *     back to queuing for the next plan when this returns {@code false})
     */
    public boolean steer(@Nullable String stepId, String directive) {
        if (directive == null || directive.isBlank()) {
            return false;
        }
        // Belt-and-suspenders: record the directive so every subsequent step's worker also sees
        // it (via augmentGoal), even if the live injection below lands on a step that's wrapping
        // up.
        steerDirectives.add(directive.trim());

        Msg msg = Msg.of(MsgRole.USER, "[用户实时干预] " + directive.trim());
        boolean hit = false;
        if (stepId != null && !stepId.isBlank()) {
            ActiveStep target = activeStepAgents.get(stepId);
            if (target != null) {
                hit = injectAndPublish(target, stepId, directive, msg);
            }
        } else {
            for (Map.Entry<String, ActiveStep> e : activeStepAgents.entrySet()) {
                hit |= injectAndPublish(e.getValue(), e.getKey(), directive, msg);
            }
        }
        return hit;
    }

    /** Enable Level-2 team self-evolution: record successful compositions + recall at planning. */
    public void setTeamPatternStore(@Nullable TeamPatternStore store) {
        this.teamPatternStore = store;
    }

    /**
     * Enable workspace-context injection: when set, the coordinator gathers a shared workspace
     * snapshot once per execution and publishes it via Reactor Context so each worker's prompt is
     * augmented with the slices its role declared (see {@code ContextScope}), without redundant
     * exploration tool calls.
     */
    public void setWorkspaceContextGatherer(@Nullable WorkspaceContextGatherer gatherer) {
        this.contextGatherer = gatherer;
    }

    /**
     * Return a request whose goal is augmented with recalled team-collaboration patterns (L2
     * recall), so the planner reuses expert compositions that worked for similar past tasks. No-op
     * (returns the original request) when L2 is disabled or nothing relevant is recalled.
     */
    private TeamExecutionRequest requestForPlanning(TeamExecutionRequest request) {
        TeamPatternStore store = this.teamPatternStore;
        if (store == null) {
            return request;
        }
        List<TeamPattern> patterns;
        try {
            patterns = store.recall(request.goal(), 3);
        } catch (RuntimeException ex) {
            log.debug("Team pattern recall failed: {}", ex.toString());
            return request;
        }
        if (patterns.isEmpty()) {
            return request;
        }
        StringBuilder sb = new StringBuilder(request.goal() == null ? "" : request.goal());
        sb.append(
                "\n\n[Learned team compositions — for similar past tasks these expert sequences"
                        + " worked; reuse when they fit]");
        for (TeamPattern p : patterns) {
            sb.append("\n- roles: ")
                    .append(String.join(" → ", p.roleSequence()))
                    .append(" (")
                    .append(p.dagShape())
                    .append(")")
                    .append(p.success() ? " [succeeded]" : " [failed]");
        }
        return new TeamExecutionRequest(
                request.requestId(), sb.toString(), request.context(), request.config());
    }

    /**
     * Record the executed team composition as a learned pattern (L2 write-back). Fire-and-forget;
     * deterministic (roleSequence + DAG shape from the plan, success from the terminal state) — no
     * extra LLM call.
     */
    private void recordTeamPatternFireAndForget(
            TeamExecutionRequest request, @Nullable TeamExecutionPlan plan, boolean success) {
        TeamPatternStore store = this.teamPatternStore;
        if (store == null || plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            return;
        }
        try {
            List<String> roleSequence =
                    plan.steps().stream()
                            .map(s -> s.assignedRole() != null ? s.assignedRole().roleId() : "?")
                            .toList();
            int maxDeps =
                    plan.steps().stream()
                            .mapToInt(s -> s.dependsOn() == null ? 0 : s.dependsOn().size())
                            .max()
                            .orElse(0);
            String dagShape = maxDeps == 0 ? "parallel:" + plan.steps().size() : "serial";
            TeamPattern pattern =
                    new TeamPattern(
                            request.goal() == null ? "" : request.goal(),
                            roleSequence,
                            dagShape,
                            success,
                            success ? 1.0 : 0.0,
                            Instant.now());
            store.record(pattern)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            v -> {},
                            e -> log.debug("Team pattern record error: {}", e.getMessage()));
        } catch (RuntimeException ex) {
            log.debug("Team pattern capture skipped: {}", ex.toString());
        }
    }

    /**
     * Append any accumulated mid-flight {@link #steerDirectives} to a step's goal so subsequent
     * steps incorporate user directives that arrived during execution. No-op when none recorded.
     */
    private String augmentGoal(String goal) {
        if (steerDirectives.isEmpty()) {
            return goal;
        }
        StringBuilder sb = new StringBuilder(goal == null ? "" : goal);
        sb.append("\n\n[Mid-flight user directives — incorporate these into your work]");
        for (String d : steerDirectives) {
            sb.append("\n- ").append(d);
        }
        return sb.toString();
    }

    private boolean injectAndPublish(ActiveStep active, String stepId, String directive, Msg msg) {
        try {
            active.agent().injectMessages(List.of(msg));
            publish(
                    active.team(),
                    active.request(),
                    TeamEventType.STEP_STEERED,
                    Map.of("stepId", stepId, "directive", trimForEvent(directive)));
            log.info(
                    "Mid-flight steer injected into step {} ({} chars)",
                    stepId,
                    directive.length());
            return true;
        } catch (RuntimeException ex) {
            log.warn("steer injectMessages failed for step {}: {}", stepId, ex.toString());
            return false;
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

    // ── ReadFileCache infrastructure ─────────────────────────────────────────

    private static final Field CACHE_TOOL_EXECUTOR_FIELD;

    static {
        Field f = null;
        try {
            f = DefaultReActAgent.class.getDeclaredField("toolExecutor");
            f.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            LoggerFactory.getLogger(ExpertTeamCoordinator.class)
                    .warn("Cannot access toolExecutor field; ReadFileCache disabled", e);
        }
        CACHE_TOOL_EXECUTOR_FIELD = f;
    }

    /**
     * Install a {@link CachingToolExecutor} wrapping each agent's current executor. Returns a map
     * of agent → original executor for later restoration. Agents that are not {@link
     * DefaultReActAgent} or whose field is inaccessible are silently skipped.
     */
    private Map<Agent, ToolExecutor> installCachingExecutors(
            Map<String, Agent> bindings, ReadFileCache cache) {
        Map<Agent, ToolExecutor> originals = new HashMap<>();
        if (CACHE_TOOL_EXECUTOR_FIELD == null) {
            return originals;
        }
        for (Agent agent : bindings.values()) {
            if (!(agent instanceof DefaultReActAgent)) {
                continue;
            }
            try {
                ToolExecutor original = (ToolExecutor) CACHE_TOOL_EXECUTOR_FIELD.get(agent);
                if (original != null && !(original instanceof CachingToolExecutor)) {
                    CachingToolExecutor cached = new CachingToolExecutor(original, cache);
                    CACHE_TOOL_EXECUTOR_FIELD.set(agent, cached);
                    originals.put(agent, original);
                }
            } catch (IllegalAccessException e) {
                log.warn("Failed to install CachingToolExecutor for agent '{}'", agent.name(), e);
            }
        }
        if (!originals.isEmpty()) {
            log.debug("ReadFileCache installed on {} agent(s)", originals.size());
        }
        return originals;
    }

    /** Restore original executors after team execution completes. */
    private void uninstallCachingExecutors(Map<Agent, ToolExecutor> originals) {
        if (CACHE_TOOL_EXECUTOR_FIELD == null) {
            return;
        }
        for (var entry : originals.entrySet()) {
            try {
                CACHE_TOOL_EXECUTOR_FIELD.set(entry.getKey(), entry.getValue());
            } catch (IllegalAccessException e) {
                log.warn(
                        "Failed to restore original executor for agent '{}'",
                        entry.getKey().name(),
                        e);
            }
        }
    }
}
