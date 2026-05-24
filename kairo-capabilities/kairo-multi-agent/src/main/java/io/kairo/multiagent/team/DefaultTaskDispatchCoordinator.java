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
package io.kairo.multiagent.team;

import io.kairo.api.agent.Agent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamCoordinator;
import io.kairo.api.team.TeamEvent;
import io.kairo.api.team.TeamEventType;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResourceConstraint;
import io.kairo.api.team.TeamResult;
import io.kairo.api.team.TeamStatus;
import io.kairo.api.team.TeamStep;
import io.kairo.multiagent.team.internal.InternalTaskBoard;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Task-board dispatch {@link TeamCoordinator} — the v0.10 replacement for the legacy {@code
 * DefaultTeamScheduler} (ADR-015, ADR-016).
 *
 * <p>Choreography:
 *
 * <ol>
 *   <li>Derive a {@link TeamExecutionPlan} from the request (explicit plan in {@code
 *       context[PLAN_CONTEXT_KEY]} or a synthesized single-step plan).
 *   <li>Build an internal DAG-keyed task board.
 *   <li>Emit {@link TeamEventType#TEAM_STARTED}.
 *   <li>Loop: dispatch ready steps to role-bound agents, up to the effective parallelism budget
 *       ({@code min(maxParallelSteps, agents.size())}). Each dispatch emits {@link
 *       TeamEventType#STEP_ASSIGNED} before invoking the agent and {@link
 *       TeamEventType#STEP_COMPLETED} when the agent's Mono terminates.
 *   <li>Wait for all in-flight dispatches, then re-evaluate readiness.
 * </ol>
 *
 * <p>Failure semantics (ADR-015 §"Failure semantics"):
 *
 * <ul>
 *   <li><b>Generator failure</b>: each step gets {@code min(maxFeedbackRounds, 3)} attempts. If all
 *       retries fail, the step is SKIPPED with a warning; remaining steps continue if their
 *       dependencies are still satisfiable. If every ready step is stuck, the team aborts with
 *       {@link TeamStatus#FAILED}.
 *   <li><b>Team timeout</b>: bounded by {@code min(teamTimeout, resourceConstraint.maxDuration)}.
 *       On breach, the coordinator emits {@link TeamEventType#TEAM_TIMEOUT}, cancels in-flight
 *       agents, and returns a partial {@link TeamResult} with {@link TeamStatus#TIMEOUT}.
 *   <li><b>Planner failure</b>: not reachable in this coordinator because plan derivation is a
 *       local transform rather than a separate LLM call; the ADR's {@code FAIL_FAST} default is
 *       honoured trivially — any exception building the plan surfaces as {@link TeamStatus#FAILED}.
 * </ul>
 *
 * <p>Evaluation semantics: this coordinator does not run an {@link
 * io.kairo.api.team.EvaluationStrategy}. It emits a synthetic PASS verdict for each successful step
 * so the {@link TeamResult.StepOutcome} record is well-formed; the richer plan → generate →
 * evaluate loop lives in {@code kairo-expert-team}'s {@code ExpertTeamCoordinator}.
 *
 * <p>Thread-safety: one coordinator instance can safely process concurrent {@link #execute(
 * TeamExecutionRequest, Team)} calls; each call owns its own {@link InternalTaskBoard} and agent
 * lease state.
 */
public class DefaultTaskDispatchCoordinator implements TeamCoordinator {

    /**
     * Caller-facing context key used to supply a pre-built {@link TeamExecutionPlan}. If absent,
     * the coordinator synthesizes a single-step plan from {@link TeamExecutionRequest#goal()}.
     */
    public static final String PLAN_CONTEXT_KEY = "kairo.team.plan";

    /**
     * Caller-facing context key used to pin a {@link TeamStep} to a specific agent id. Value type:
     * {@code Map<String, String>} (stepId → agentId).
     */
    public static final String STEP_AGENT_BINDING_KEY = "kairo.team.stepAgentBindings";

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskDispatchCoordinator.class);

    /** Upper bound on retry attempts for a single step, regardless of caller preference. */
    private static final int MAX_DISPATCH_ATTEMPTS = 3;

    @Nullable private final KairoEventBus eventBus;

    /** Create a coordinator that does not emit events (useful for tests / minimal setups). */
    public DefaultTaskDispatchCoordinator() {
        this(null);
    }

    /**
     * Create a coordinator that bridges its lifecycle events through the given {@link
     * KairoEventBus}.
     *
     * @param eventBus event bus facade; {@code null} disables event emission
     */
    public DefaultTaskDispatchCoordinator(@Nullable KairoEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public Mono<TeamResult> execute(TeamExecutionRequest request, Team team) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(team, "team must not be null");

        return Mono.defer(() -> runOnce(request, team));
    }

    private Mono<TeamResult> runOnce(TeamExecutionRequest request, Team team) {
        Instant startedAt = Instant.now();

        // 1. Derive the execution plan.
        TeamExecutionPlan plan;
        try {
            plan = resolvePlan(request);
        } catch (RuntimeException e) {
            log.warn(
                    "Planner rejected request '{}' for team '{}': {}",
                    request.requestId(),
                    team.name(),
                    e.toString());
            emit(
                    TeamEventType.TEAM_FAILED,
                    team,
                    request.requestId(),
                    Map.of("reason", "planner-failure", "error", e.toString()));
            return Mono.just(
                    TeamResult.withoutOutput(
                            request.requestId(),
                            TeamStatus.FAILED,
                            List.of(),
                            Duration.between(startedAt, Instant.now()),
                            List.of("planner-failure: " + e.getMessage())));
        }

        // 2. Build the internal board.
        InternalTaskBoard board;
        try {
            board = new InternalTaskBoard(plan.steps());
        } catch (RuntimeException e) {
            emit(
                    TeamEventType.TEAM_FAILED,
                    team,
                    request.requestId(),
                    Map.of("reason", "invalid-plan", "error", e.toString()));
            return Mono.just(
                    TeamResult.withoutOutput(
                            request.requestId(),
                            TeamStatus.FAILED,
                            List.of(),
                            Duration.between(startedAt, Instant.now()),
                            List.of("invalid-plan: " + e.getMessage())));
        }

        if (team.agents().isEmpty()) {
            emit(
                    TeamEventType.TEAM_FAILED,
                    team,
                    request.requestId(),
                    Map.of("reason", "no-agents"));
            return Mono.just(
                    TeamResult.withoutOutput(
                            request.requestId(),
                            TeamStatus.FAILED,
                            List.of(),
                            Duration.between(startedAt, Instant.now()),
                            List.of("team has no agents")));
        }

        // 3. Kick off.
        TeamConfig config = request.config();
        TeamResourceConstraint constraint = config.resourceConstraint();
        Duration effectiveDeadline = minimum(config.teamTimeout(), constraint.maxDuration());
        Map<String, String> stepAgentBindings = extractBindings(request);
        AgentLeaseRegistry leases = new AgentLeaseRegistry(team.agents());

        emit(
                TeamEventType.TEAM_STARTED,
                team,
                request.requestId(),
                Map.of(
                        "planId",
                        plan.planId(),
                        "totalSteps",
                        plan.totalSteps(),
                        "maxParallelSteps",
                        Math.min(constraint.maxParallelSteps(), team.agents().size())));

        Mono<TeamResult> loop =
                dispatchLoop(request, team, plan, board, leases, stepAgentBindings, startedAt)
                        .onErrorResume(
                                throwable -> {
                                    log.error(
                                            "Dispatch loop failed for request '{}' on team '{}'",
                                            request.requestId(),
                                            team.name(),
                                            throwable);
                                    emit(
                                            TeamEventType.TEAM_FAILED,
                                            team,
                                            request.requestId(),
                                            Map.of("error", throwable.toString()));
                                    return Mono.just(
                                            assembleResult(
                                                    request,
                                                    board,
                                                    TeamStatus.FAILED,
                                                    startedAt,
                                                    List.of(
                                                            "dispatch-failure: "
                                                                    + throwable.getMessage())));
                                });

        return loop.timeout(
                effectiveDeadline,
                Mono.fromSupplier(
                        () -> {
                            emit(
                                    TeamEventType.TEAM_TIMEOUT,
                                    team,
                                    request.requestId(),
                                    Map.of(
                                            "deadline",
                                            effectiveDeadline.toString(),
                                            "inFlight",
                                            board.inFlightCount()));
                            return assembleResult(
                                    request,
                                    board,
                                    TeamStatus.TIMEOUT,
                                    startedAt,
                                    List.of("team timed out after " + effectiveDeadline));
                        }));
    }

    private Mono<TeamResult> dispatchLoop(
            TeamExecutionRequest request,
            Team team,
            TeamExecutionPlan plan,
            InternalTaskBoard board,
            AgentLeaseRegistry leases,
            Map<String, String> bindings,
            Instant startedAt) {

        TeamConfig config = request.config();
        int parallelismCap =
                Math.max(
                        1,
                        Math.min(
                                config.resourceConstraint().maxParallelSteps(),
                                team.agents().size()));

        return Mono.defer(
                        () -> {
                            if (board.allTerminal()) {
                                // Terminal — exit the loop; trailing .then() finalizes exactly
                                // once.
                                return Mono.empty();
                            }

                            List<TeamStep> ready = board.readySteps();
                            if (ready.isEmpty() && board.inFlightCount() == 0) {
                                // Stuck — no ready steps and nothing in flight. Mark every
                                // non-terminal step as FAILED so allTerminal() flips true and the
                                // repeat loop exits naturally. Finalization happens once at the
                                // end of the chain.
                                failStuckSteps(board, "dispatch stuck: no ready steps in flight");
                                return Mono.empty();
                            }

                            int slotsAvailable = parallelismCap - board.inFlightCount();
                            if (slotsAvailable <= 0 || ready.isEmpty()) {
                                // Wait a short tick for in-flight work; Reactor's take/merge will
                                // drive us back here when dispatches complete.
                                return Mono.delay(Duration.ofMillis(5)).then(Mono.empty());
                            }

                            List<Mono<Void>> dispatches = new ArrayList<>();
                            for (TeamStep step : ready) {
                                if (dispatches.size() >= slotsAvailable) {
                                    break;
                                }
                                Agent agent = leases.claimFor(step, bindings);
                                if (agent == null) {
                                    // No agent available right now — break and wait.
                                    break;
                                }
                                board.markInFlight(step.stepId());
                                dispatches.add(
                                        dispatchOnce(request, team, step, agent, board, leases)
                                                .subscribeOn(Schedulers.parallel()));
                            }

                            if (dispatches.isEmpty()) {
                                return Mono.delay(Duration.ofMillis(5)).then(Mono.empty());
                            }

                            return Flux.merge(dispatches).then(Mono.empty());
                        })
                .repeat(() -> !board.allTerminal())
                .then(Mono.fromSupplier(() -> finalizeResult(request, team, board, startedAt)));
    }

    /**
     * Mark every non-terminal step (PENDING or IN_FLIGHT) as FAILED with the supplied reason. Used
     * when the dispatcher detects a stuck DAG so the repeat loop can exit naturally and {@link
     * #finalizeResult} runs exactly once.
     */
    private void failStuckSteps(InternalTaskBoard board, String reason) {
        for (String stepId : board.nonTerminalStepIds()) {
            board.recordFailure(stepId, reason);
            board.markFailed(stepId);
        }
    }

    private Mono<Void> dispatchOnce(
            TeamExecutionRequest request,
            Team team,
            TeamStep step,
            Agent agent,
            InternalTaskBoard board,
            AgentLeaseRegistry leases) {

        emit(
                TeamEventType.STEP_ASSIGNED,
                team,
                request.requestId(),
                Map.of(
                        "stepId",
                        step.stepId(),
                        "stepIndex",
                        step.stepIndex(),
                        "agentId",
                        agent.id(),
                        "roleId",
                        step.assignedRole().roleId(),
                        "attempt",
                        board.attemptsOf(step.stepId())));

        Msg prompt = buildPrompt(request, step);
        return agent.call(prompt)
                .flatMap(
                        response -> {
                            String artifact = response == null ? "" : response.text();
                            board.markCompleted(step.stepId(), artifact);
                            emit(
                                    TeamEventType.STEP_COMPLETED,
                                    team,
                                    request.requestId(),
                                    Map.of(
                                            "stepId",
                                            step.stepId(),
                                            "agentId",
                                            agent.id(),
                                            "attempts",
                                            board.attemptsOf(step.stepId()),
                                            "artifactLength",
                                            artifact.length()));
                            leases.release(agent, step);
                            return Mono.<Void>empty();
                        })
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Step '{}' failed on agent '{}' (attempt {}): {}",
                                    step.stepId(),
                                    agent.id(),
                                    board.attemptsOf(step.stepId()),
                                    e.toString());
                            leases.release(agent, step);
                            handleDispatchFailure(request, team, step, agent, board, e);
                            return Mono.empty();
                        })
                .then();
    }

    private void handleDispatchFailure(
            TeamExecutionRequest request,
            Team team,
            TeamStep step,
            Agent agent,
            InternalTaskBoard board,
            Throwable error) {

        int maxAttempts =
                Math.min(
                        MAX_DISPATCH_ATTEMPTS,
                        Math.max(1, request.config().resourceConstraint().maxFeedbackRounds()));
        int attempts = board.attemptsOf(step.stepId());

        if (attempts < maxAttempts) {
            // Requeue for another attempt by another (or the same) agent.
            board.markPending(step.stepId());
            board.recordFailure(step.stepId(), error.toString());
        } else {
            // Exhausted retries — skip this step so other branches can still run.
            board.markSkipped(
                    step.stepId(),
                    "generator-failure-after-" + attempts + "-attempts: " + error.getMessage());
            emit(
                    TeamEventType.STEP_COMPLETED,
                    team,
                    request.requestId(),
                    Map.of(
                            "stepId",
                            step.stepId(),
                            "agentId",
                            agent.id(),
                            "attempts",
                            attempts,
                            "skipped",
                            true,
                            "error",
                            error.toString()));
        }
    }

    private TeamResult finalizeResult(
            TeamExecutionRequest request, Team team, InternalTaskBoard board, Instant startedAt) {
        TeamStatus status = inferTerminalStatus(board);
        List<String> warnings = collectWarnings(board);
        emit(
                status == TeamStatus.COMPLETED
                        ? TeamEventType.TEAM_COMPLETED
                        : TeamEventType.TEAM_FAILED,
                team,
                request.requestId(),
                Map.of("status", status.name(), "stepCount", board.allSteps().size()));
        return assembleResult(request, board, status, startedAt, warnings);
    }

    private TeamStatus inferTerminalStatus(InternalTaskBoard board) {
        if (board.anyFailed()) {
            return TeamStatus.FAILED;
        }
        boolean anySkipped = false;
        boolean anyCompleted = false;
        for (InternalTaskBoard.StepRecord r : board.snapshot()) {
            switch (r.state()) {
                case SKIPPED -> anySkipped = true;
                case COMPLETED -> anyCompleted = true;
                case PENDING, IN_FLIGHT -> {
                    // Should not be reached once we finalize, but treat as failure.
                    return TeamStatus.FAILED;
                }
                default -> {}
            }
        }
        if (anySkipped && !anyCompleted) {
            return TeamStatus.FAILED;
        }
        if (anySkipped) {
            return TeamStatus.DEGRADED;
        }
        return TeamStatus.COMPLETED;
    }

    private List<String> collectWarnings(InternalTaskBoard board) {
        List<String> warnings = new ArrayList<>();
        for (InternalTaskBoard.StepRecord r : board.snapshot()) {
            if (r.state() == InternalTaskBoard.State.SKIPPED && r.failureReason() != null) {
                warnings.add("step " + r.step().stepId() + " skipped: " + r.failureReason());
            }
        }
        return warnings;
    }

    private TeamResult assembleResult(
            TeamExecutionRequest request,
            InternalTaskBoard board,
            TeamStatus status,
            Instant startedAt,
            List<String> extraWarnings) {

        List<TeamResult.StepOutcome> outcomes = new ArrayList<>();
        StringBuilder finalOutput = new StringBuilder();
        boolean anyArtifact = false;

        for (InternalTaskBoard.StepRecord r : board.snapshot()) {
            EvaluationVerdict verdict = verdictFor(r);
            if (r.state() == InternalTaskBoard.State.COMPLETED) {
                outcomes.add(
                        new TeamResult.StepOutcome(
                                r.step().stepId(),
                                Objects.requireNonNullElse(r.artifact(), ""),
                                verdict,
                                Math.max(1, r.attempts())));
                if (r.artifact() != null && !r.artifact().isEmpty()) {
                    if (anyArtifact) {
                        finalOutput.append("\n\n");
                    }
                    finalOutput.append(r.artifact());
                    anyArtifact = true;
                }
            } else if (r.state() == InternalTaskBoard.State.SKIPPED) {
                outcomes.add(
                        new TeamResult.StepOutcome(
                                r.step().stepId(), "", verdict, Math.max(1, r.attempts())));
            }
            // PENDING / IN_FLIGHT / FAILED records do not produce an outcome entry by default —
            // except for the TIMEOUT path below which still wants a record for observability.
        }

        Duration duration = Duration.between(startedAt, Instant.now());
        List<String> warnings = new ArrayList<>(extraWarnings);
        warnings.addAll(collectWarnings(board));
        List<String> deduped = warnings.stream().distinct().toList();

        if (status == TeamStatus.TIMEOUT) {
            // Include unfinished steps as outcomes with REVIEW_EXCEEDED to make the partial result
            // legible to the caller.
            for (InternalTaskBoard.StepRecord r : board.snapshot()) {
                if (r.state() == InternalTaskBoard.State.PENDING
                        || r.state() == InternalTaskBoard.State.IN_FLIGHT) {
                    outcomes.add(
                            new TeamResult.StepOutcome(
                                    r.step().stepId(),
                                    "",
                                    new EvaluationVerdict(
                                            EvaluationVerdict.VerdictOutcome.REVIEW_EXCEEDED,
                                            0.0,
                                            "cancelled on team timeout",
                                            List.of(),
                                            Instant.now()),
                                    Math.max(1, r.attempts())));
                }
            }
        }

        if (anyArtifact && status == TeamStatus.COMPLETED) {
            return TeamResult.of(
                    request.requestId(),
                    status,
                    outcomes,
                    finalOutput.toString(),
                    duration,
                    deduped);
        }
        return TeamResult.withoutOutput(request.requestId(), status, outcomes, duration, deduped);
    }

    private EvaluationVerdict verdictFor(InternalTaskBoard.StepRecord record) {
        Instant now = Instant.now();
        return switch (record.state()) {
            case COMPLETED ->
                    new EvaluationVerdict(
                            EvaluationVerdict.VerdictOutcome.PASS,
                            1.0,
                            "task-dispatch PASS (no evaluator configured)",
                            List.of(),
                            now);
            case SKIPPED ->
                    new EvaluationVerdict(
                            EvaluationVerdict.VerdictOutcome.REVIEW_EXCEEDED,
                            0.0,
                            Objects.requireNonNullElse(
                                    record.failureReason(), "retry budget exhausted"),
                            List.of(),
                            now);
            case FAILED ->
                    new EvaluationVerdict(
                            EvaluationVerdict.VerdictOutcome.REVIEW_EXCEEDED,
                            0.0,
                            "step failed",
                            List.of(),
                            now);
            default ->
                    new EvaluationVerdict(
                            EvaluationVerdict.VerdictOutcome.REVIEW_EXCEEDED,
                            0.0,
                            "not terminal",
                            List.of(),
                            now);
        };
    }

    private Msg buildPrompt(TeamExecutionRequest request, TeamStep step) {
        RoleDefinition role = step.assignedRole();
        String text =
                "Goal: "
                        + request.goal()
                        + "\n\nRole: "
                        + role.roleName()
                        + "\nInstructions: "
                        + role.instructions()
                        + "\n\nStep "
                        + (step.stepIndex() + 1)
                        + " ("
                        + step.stepId()
                        + "): "
                        + step.description();
        return Msg.of(MsgRole.USER, text);
    }

    @SuppressWarnings("unchecked")
    private TeamExecutionPlan resolvePlan(TeamExecutionRequest request) {
        Object supplied = request.context().get(PLAN_CONTEXT_KEY);
        if (supplied instanceof TeamExecutionPlan plan) {
            return plan;
        }
        if (supplied != null) {
            throw new IllegalArgumentException(
                    "context["
                            + PLAN_CONTEXT_KEY
                            + "] must be a TeamExecutionPlan, got "
                            + supplied.getClass().getName());
        }
        // Synthesize a one-step plan.
        RoleDefinition defaultRole =
                new RoleDefinition(
                        "default-worker",
                        "Default Worker",
                        "Produce the requested artifact.",
                        "generic",
                        List.of());
        TeamStep step = new TeamStep("step-1", request.goal(), defaultRole, List.of(), 0);
        return new TeamExecutionPlan("plan-" + UUID.randomUUID(), List.of(step), Instant.now());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractBindings(TeamExecutionRequest request) {
        Object raw = request.context().get(STEP_AGENT_BINDING_KEY);
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> out = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String k && entry.getValue() instanceof String v) {
                    out.put(k, v);
                }
            }
            return out;
        }
        return Map.of();
    }

    private static Duration minimum(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private void emit(
            TeamEventType type, Team team, String requestId, Map<String, Object> attributes) {
        if (eventBus == null) {
            return;
        }
        try {
            TeamEvent event =
                    new TeamEvent(type, team.name(), requestId, Instant.now(), attributes);
            eventBus.publish(event.toKairoEvent());
        } catch (RuntimeException e) {
            log.debug("Failed to emit TeamEvent {}: {}", type, e.toString());
        }
    }

    /**
     * Tracks which agent is currently handling which step so we can honour explicit bindings and
     * round-robin the unbound steps across remaining agents.
     */
    private static final class AgentLeaseRegistry {

        private final List<Agent> agents;
        private final ConcurrentHashMap<String, String> inFlightByAgent = new ConcurrentHashMap<>();
        private final AtomicInteger cursor = new AtomicInteger(0);

        AgentLeaseRegistry(List<Agent> agents) {
            this.agents = agents;
        }

        synchronized @Nullable Agent claimFor(TeamStep step, Map<String, String> bindings) {
            String pinnedId = bindings.get(step.stepId());
            if (pinnedId != null) {
                Agent pinned = findById(pinnedId);
                if (pinned == null) {
                    throw new IllegalArgumentException(
                            "stepAgentBindings references unknown agent id '" + pinnedId + "'");
                }
                if (inFlightByAgent.containsKey(pinnedId)) {
                    return null; // pinned agent is busy; wait for it.
                }
                inFlightByAgent.put(pinnedId, step.stepId());
                return pinned;
            }
            for (int i = 0; i < agents.size(); i++) {
                int idx = Math.floorMod(cursor.getAndIncrement(), agents.size());
                Agent candidate = agents.get(idx);
                if (!inFlightByAgent.containsKey(candidate.id())) {
                    inFlightByAgent.put(candidate.id(), step.stepId());
                    return candidate;
                }
            }
            return null;
        }

        synchronized void release(Agent agent, TeamStep step) {
            inFlightByAgent.remove(agent.id());
        }

        @Nullable
        Agent findById(String id) {
            for (Agent a : agents) {
                if (a.id().equals(id)) {
                    return a;
                }
            }
            return null;
        }
    }
}
