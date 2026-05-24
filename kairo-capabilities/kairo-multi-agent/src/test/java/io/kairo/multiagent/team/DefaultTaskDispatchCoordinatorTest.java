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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamEvent;
import io.kairo.api.team.TeamEventType;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResourceConstraint;
import io.kairo.api.team.TeamResult;
import io.kairo.api.team.TeamStatus;
import io.kairo.api.team.TeamStep;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Unit tests for {@link DefaultTaskDispatchCoordinator}. */
class DefaultTaskDispatchCoordinatorTest {

    private static Team teamOf(List<Agent> agents) {
        return new Team("test-team", agents, new InProcessMessageBus());
    }

    private static RoleDefinition role(String id) {
        return new RoleDefinition(id, id + "-name", "do work", "generic", List.of());
    }

    private static TeamConfig configWith(TeamResourceConstraint constraint) {
        return new TeamConfig(
                RiskProfile.MEDIUM,
                3,
                Duration.ofSeconds(30),
                EvaluatorPreference.AUTO,
                PlannerFailureMode.FAIL_FAST,
                constraint);
    }

    private static TeamConfig defaultConfig() {
        return configWith(TeamResourceConstraint.unbounded());
    }

    private static TeamExecutionRequest request(
            String goal, TeamExecutionPlan plan, TeamConfig config) {
        Map<String, Object> ctx = new HashMap<>();
        if (plan != null) {
            ctx.put(DefaultTaskDispatchCoordinator.PLAN_CONTEXT_KEY, plan);
        }
        return new TeamExecutionRequest(UUID.randomUUID().toString(), goal, ctx, config);
    }

    private static TeamExecutionPlan planOf(TeamStep... steps) {
        return new TeamExecutionPlan("plan-" + UUID.randomUUID(), List.of(steps), Instant.now());
    }

    // ==================== Happy-path dispatch ====================

    @Test
    void synthesizesSingleStepPlanWhenNoneSupplied() {
        StubAgent agent = StubAgent.echo("worker-1");
        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator();

        TeamResult result =
                coordinator
                        .execute(
                                request("write a haiku", null, defaultConfig()),
                                teamOf(List.of(agent)))
                        .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(result.stepOutcomes()).hasSize(1);
        assertThat(result.stepOutcomes().get(0).finalVerdict().outcome())
                .isEqualTo(EvaluationVerdict.VerdictOutcome.PASS);
        assertThat(result.finalOutput()).isPresent();
        assertThat(agent.invocations.get()).isEqualTo(1);
    }

    @Test
    void dispatchesDagInDependencyOrder() {
        StubAgent a = StubAgent.echo("a");
        StubAgent b = StubAgent.echo("b");

        TeamStep s1 = new TeamStep("s1", "root", role("r1"), List.of(), 0);
        TeamStep s2 = new TeamStep("s2", "leaf", role("r2"), List.of("s1"), 1);
        TeamExecutionPlan plan = planOf(s1, s2);

        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator();
        TeamResult result =
                coordinator
                        .execute(request("two-step", plan, defaultConfig()), teamOf(List.of(a, b)))
                        .block(Duration.ofSeconds(5));

        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(result.stepOutcomes()).hasSize(2);
        assertThat(result.stepOutcomes())
                .extracting(TeamResult.StepOutcome::stepId)
                .containsExactly("s1", "s2");
    }

    // ==================== Parallelism cap ====================

    @Test
    void parallelismCapIsMinOfConstraintAndAgents() {
        // Three parallel steps, two agents, constraint allows 4 — actual cap becomes 2.
        ConcurrentLinkedQueue<Integer> inFlightSamples = new ConcurrentLinkedQueue<>();
        AtomicInteger inFlight = new AtomicInteger();

        StubAgent a1 =
                StubAgent.recordingSlow(
                        "agent-1", inFlight, inFlightSamples, Duration.ofMillis(50));
        StubAgent a2 =
                StubAgent.recordingSlow(
                        "agent-2", inFlight, inFlightSamples, Duration.ofMillis(50));

        TeamExecutionPlan plan =
                planOf(
                        new TeamStep("p1", "x", role("r"), List.of(), 0),
                        new TeamStep("p2", "y", role("r"), List.of(), 1),
                        new TeamStep("p3", "z", role("r"), List.of(), 2));

        TeamConfig cfg =
                configWith(
                        new TeamResourceConstraint(
                                Long.MAX_VALUE, Duration.ofMinutes(1), 4, Integer.MAX_VALUE));

        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator();
        TeamResult result =
                coordinator
                        .execute(request("parallel", plan, cfg), teamOf(List.of(a1, a2)))
                        .block(Duration.ofSeconds(5));

        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        // Parallelism cap should be 2; no sample must exceed 2 concurrent dispatches.
        assertThat(inFlightSamples).isNotEmpty();
        assertThat(inFlightSamples).allSatisfy(sample -> assertThat(sample).isLessThanOrEqualTo(2));
    }

    // ==================== Failure handling ====================

    @Test
    void retriesThenSkipsAfterExhaustion() {
        StubAgent flaky =
                StubAgent.failing("flaky", new RuntimeException("boom"), Integer.MAX_VALUE);
        TeamExecutionPlan plan = planOf(new TeamStep("only", "fail", role("r"), List.of(), 0));

        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator();
        TeamResult result =
                coordinator
                        .execute(request("flaky", plan, defaultConfig()), teamOf(List.of(flaky)))
                        .block(Duration.ofSeconds(5));

        // With every step skipped and no completions, the team is FAILED per the
        // coordinator's terminal-status rule.
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);
        assertThat(result.warnings()).isNotEmpty();
        // Should have tried at least three times (default config maxFeedbackRounds=3,
        // bounded by MAX_DISPATCH_ATTEMPTS=3).
        assertThat(flaky.invocations.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void degradedWhenSomeStepsSkippedButOthersComplete() {
        StubAgent good = StubAgent.echo("good");
        StubAgent bad = StubAgent.failing("bad", new RuntimeException("nope"), Integer.MAX_VALUE);

        TeamExecutionPlan plan =
                planOf(
                        new TeamStep("ok", "ok", role("r"), List.of(), 0),
                        new TeamStep("ko", "ko", role("r"), List.of(), 1));

        Map<String, Object> ctx = new HashMap<>();
        ctx.put(DefaultTaskDispatchCoordinator.PLAN_CONTEXT_KEY, plan);
        Map<String, String> bindings = new HashMap<>();
        bindings.put("ok", "good");
        bindings.put("ko", "bad");
        ctx.put(DefaultTaskDispatchCoordinator.STEP_AGENT_BINDING_KEY, bindings);
        TeamExecutionRequest req =
                new TeamExecutionRequest(
                        UUID.randomUUID().toString(), "mixed", ctx, defaultConfig());

        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator();
        TeamResult result =
                coordinator.execute(req, teamOf(List.of(good, bad))).block(Duration.ofSeconds(5));

        assertThat(result.status()).isEqualTo(TeamStatus.DEGRADED);
        assertThat(result.warnings()).anyMatch(w -> w.contains("ko"));
    }

    // ==================== Timeout ====================

    @Test
    void timeoutBreachReturnsPartialResult() {
        StubAgent slow =
                StubAgent.delayed("slow", Msg.of(MsgRole.ASSISTANT, "done"), Duration.ofSeconds(5));
        TeamExecutionPlan plan = planOf(new TeamStep("slow-step", "slow", role("r"), List.of(), 0));

        TeamConfig tightConfig =
                new TeamConfig(
                        RiskProfile.MEDIUM,
                        3,
                        Duration.ofMillis(200),
                        EvaluatorPreference.AUTO,
                        PlannerFailureMode.FAIL_FAST,
                        TeamResourceConstraint.unbounded());

        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator();
        TeamResult result =
                coordinator
                        .execute(request("slow-team", plan, tightConfig), teamOf(List.of(slow)))
                        .block(Duration.ofSeconds(3));

        assertThat(result.status()).isEqualTo(TeamStatus.TIMEOUT);
        assertThat(result.stepOutcomes()).isNotEmpty();
        assertThat(result.stepOutcomes().get(0).finalVerdict().outcome())
                .isEqualTo(EvaluationVerdict.VerdictOutcome.REVIEW_EXCEEDED);
    }

    // ==================== Event emission ====================

    @Test
    void emitsLifecycleEventsThroughEventBus() {
        RecordingEventBus bus = new RecordingEventBus();
        StubAgent a = StubAgent.echo("a");

        TeamExecutionPlan plan = planOf(new TeamStep("s1", "only", role("r"), List.of(), 0));
        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator(bus);

        TeamResult result =
                coordinator
                        .execute(request("evt", plan, defaultConfig()), teamOf(List.of(a)))
                        .block(Duration.ofSeconds(5));

        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(bus.types())
                .contains(
                        TeamEventType.TEAM_STARTED,
                        TeamEventType.STEP_ASSIGNED,
                        TeamEventType.STEP_COMPLETED,
                        TeamEventType.TEAM_COMPLETED);
    }

    @Test
    void emitsTeamFailedWhenNoAgents() {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator(bus);

        TeamResult result =
                coordinator
                        .execute(request("empty", null, defaultConfig()), teamOf(List.of()))
                        .block(Duration.ofSeconds(3));

        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);
        assertThat(bus.types()).contains(TeamEventType.TEAM_FAILED);
    }

    @Test
    void stuckDagEmitsExactlyOneTerminalEventAndFinalizesOnce() {
        // Regression for P0: on a stuck graph (dependency skipped, dependent can never run), the
        // dispatch loop must NOT spin in a tight repeat, and finalizeResult must fire exactly once.
        // Before the fix, both the stuck branch inside the deferred Mono AND the trailing
        // .then(Mono.fromSupplier(...)) emitted TEAM_FAILED, so two terminal events appeared.
        RecordingEventBus bus = new RecordingEventBus();
        StubAgent bad = StubAgent.failing("bad", new RuntimeException("always"), Integer.MAX_VALUE);
        StubAgent idle = StubAgent.echo("idle"); // bound to s2, never reachable

        TeamExecutionPlan plan =
                planOf(
                        new TeamStep("s1", "root", role("r"), List.of(), 0),
                        new TeamStep("s2", "leaf", role("r"), List.of("s1"), 1));

        Map<String, Object> ctx = new HashMap<>();
        ctx.put(DefaultTaskDispatchCoordinator.PLAN_CONTEXT_KEY, plan);
        Map<String, String> bindings = new HashMap<>();
        bindings.put("s1", "bad");
        bindings.put("s2", "idle");
        ctx.put(DefaultTaskDispatchCoordinator.STEP_AGENT_BINDING_KEY, bindings);
        TeamExecutionRequest req =
                new TeamExecutionRequest(
                        UUID.randomUUID().toString(), "stuck", ctx, defaultConfig());

        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator(bus);
        TeamResult result =
                coordinator.execute(req, teamOf(List.of(bad, idle))).block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);

        // The terminal event must fire exactly once.
        long terminalCount =
                bus.types().stream()
                        .filter(
                                t ->
                                        t == TeamEventType.TEAM_COMPLETED
                                                || t == TeamEventType.TEAM_FAILED)
                        .count();
        assertThat(terminalCount).isEqualTo(1L);

        // s2 should never have been dispatched to its bound agent (dependency was skipped).
        assertThat(idle.invocations.get()).isEqualTo(0);
    }

    @Test
    void rejectsPlanOfWrongTypeInContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(DefaultTaskDispatchCoordinator.PLAN_CONTEXT_KEY, "not-a-plan");
        TeamExecutionRequest req =
                new TeamExecutionRequest(UUID.randomUUID().toString(), "g", ctx, defaultConfig());

        DefaultTaskDispatchCoordinator coordinator = new DefaultTaskDispatchCoordinator();
        TeamResult result =
                coordinator
                        .execute(req, teamOf(List.of(StubAgent.echo("a"))))
                        .block(Duration.ofSeconds(3));

        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);
        assertThat(result.warnings()).anyMatch(w -> w.contains("planner-failure"));
    }

    // ==================== Helpers ====================

    private static final class StubAgent implements Agent {
        private final String id;
        final AtomicInteger invocations = new AtomicInteger();
        private final java.util.function.Function<Msg, Mono<Msg>> responder;

        private StubAgent(String id, java.util.function.Function<Msg, Mono<Msg>> responder) {
            this.id = id;
            this.responder = responder;
        }

        static StubAgent echo(String id) {
            return new StubAgent(
                    id, msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "response from " + id)));
        }

        static StubAgent delayed(String id, Msg response, Duration delay) {
            return new StubAgent(id, msg -> Mono.just(response).delayElement(delay));
        }

        static StubAgent failing(String id, RuntimeException error, int maxFailures) {
            AtomicInteger failures = new AtomicInteger();
            return new StubAgent(
                    id,
                    msg -> {
                        if (failures.getAndIncrement() < maxFailures) {
                            return Mono.error(error);
                        }
                        return Mono.just(Msg.of(MsgRole.ASSISTANT, "recovered"));
                    });
        }

        static StubAgent recordingSlow(
                String id,
                AtomicInteger inFlight,
                ConcurrentLinkedQueue<Integer> samples,
                Duration delay) {
            return new StubAgent(
                    id,
                    msg ->
                            Mono.fromSupplier(
                                            () -> {
                                                int current = inFlight.incrementAndGet();
                                                samples.add(current);
                                                return current;
                                            })
                                    .delayElement(delay)
                                    .map(
                                            current -> {
                                                inFlight.decrementAndGet();
                                                return Msg.of(MsgRole.ASSISTANT, "done:" + id);
                                            }));
        }

        @Override
        public Mono<Msg> call(Msg input) {
            invocations.incrementAndGet();
            return responder.apply(input);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return id + "-name";
        }

        @Override
        public AgentState state() {
            return AgentState.IDLE;
        }

        @Override
        public void interrupt() {}
    }

    private static final class RecordingEventBus implements KairoEventBus {
        private final List<KairoEvent> events = new CopyOnWriteArrayList<>();
        private final Sinks.Many<KairoEvent> sink = Sinks.many().replay().all();

        @Override
        public void publish(KairoEvent event) {
            events.add(event);
            sink.tryEmitNext(event);
        }

        @Override
        public Flux<KairoEvent> subscribe() {
            return sink.asFlux();
        }

        @Override
        public Flux<KairoEvent> subscribe(String domain) {
            return sink.asFlux().filter(e -> e.domain().equals(domain));
        }

        List<TeamEventType> types() {
            List<TeamEventType> out = new ArrayList<>();
            for (KairoEvent e : events) {
                if (e.payload() instanceof TeamEvent te) {
                    out.add(te.type());
                }
            }
            return out;
        }
    }
}
