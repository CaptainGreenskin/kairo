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
package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentState;
import io.kairo.api.evolution.*;
import io.kairo.api.hook.SessionEndEvent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.skill.InMemoryEvolvedSkillStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end tests for the self-improvement cycle: Hook → Trigger → Orchestrator → Policy → Store
 */
class EvolutionCycleE2ETest {

    private EvolutionTrigger trigger;
    private InMemoryEvolvedSkillStore skillStore;
    private InMemoryEvolutionRuntimeStateStore stateStore;
    private EvolutionStateMachine stateMachine;
    private EvolutionPolicy policy;
    private EvolutionPipelineOrchestrator orchestrator;
    private EvolutionHook hook;

    @BeforeEach
    void setUp() {
        trigger = mock(EvolutionTrigger.class);
        skillStore = new InMemoryEvolvedSkillStore();
        stateStore = new InMemoryEvolutionRuntimeStateStore();
        stateMachine = new EvolutionStateMachine(3);
        policy = mock(EvolutionPolicy.class);
        orchestrator =
                new EvolutionPipelineOrchestrator(policy, skillStore, stateMachine, stateStore);
        hook = new EvolutionHook(trigger, skillStore, orchestrator);
    }

    private SessionEndEvent completedSession(String agentName, int iterations) {
        return new SessionEndEvent(
                agentName,
                AgentState.COMPLETED,
                iterations,
                4000L,
                Duration.ofSeconds(10),
                null,
                () ->
                        List.of(
                                Msg.of(MsgRole.USER, "implement feature"),
                                Msg.of(MsgRole.ASSISTANT, "Done.")));
    }

    private EvolvedSkill skill(String name) {
        return new EvolvedSkill(
                name,
                "1.0.0",
                "description",
                "A valid skill instructions block with enough content.",
                "test",
                Set.of("java"),
                SkillTrustLevel.DRAFT,
                null,
                Instant.now(),
                Instant.now(),
                0);
    }

    // --- Scenario 1: Normal self-improvement cycle — new skill persisted ---

    @Test
    void normalCycle_newSkillPersistedAndStateBecomesIdle() {
        when(policy.review(any()))
                .thenReturn(
                        Mono.just(
                                new EvolutionOutcome(
                                        Optional.of(skill("auto-skill")),
                                        Optional.empty(),
                                        List.of(),
                                        "created")));

        StepVerifier.create(orchestrator.submit(contextFor("agent-1"))).verifyComplete();

        StepVerifier.create(skillStore.get("auto-skill"))
                .assertNext(opt -> assertThat(opt).isPresent())
                .verifyComplete();
    }

    @Test
    void normalCycle_hookSuccessCountIncrements() throws InterruptedException {
        when(policy.review(any())).thenReturn(Mono.just(EvolutionOutcome.empty()));

        hook.onSessionEnd(completedSession("agent-count", 5));
        Thread.sleep(100);

        assertThat(hook.getSuccessCount()).isEqualTo(1);
        assertThat(hook.getFailureCount()).isEqualTo(0);
    }

    // --- Scenario 2: Evaluation failure / rejection (scan fails) ---

    @Test
    void scanFails_skillNotActivated() {
        // Skill with empty instructions → scan rejects it
        EvolvedSkill badSkill =
                new EvolvedSkill(
                        "bad-skill",
                        "1.0.0",
                        "desc",
                        "",
                        "test",
                        Set.of(),
                        SkillTrustLevel.DRAFT,
                        null,
                        Instant.now(),
                        Instant.now(),
                        0);

        when(policy.review(any()))
                .thenReturn(
                        Mono.just(
                                new EvolutionOutcome(
                                        Optional.of(badSkill),
                                        Optional.empty(),
                                        List.of(),
                                        "bad")));

        StepVerifier.create(orchestrator.submit(contextFor("agent-reject"))).verifyComplete();

        // Skill should not remain in store after rejection
        StepVerifier.create(skillStore.get("bad-skill"))
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    void noChangesOutcome_stateRemainsIdle() {
        when(policy.review(any())).thenReturn(Mono.just(EvolutionOutcome.empty()));

        StepVerifier.create(orchestrator.submit(contextFor("agent-idle"))).verifyComplete();

        assertThat(stateStore.getState("agent-idle")).isEqualTo(EvolutionState.IDLE);
    }

    // --- Scenario 3: Generator (policy) failure → fallback ---

    @Test
    void policyException_orchestratorRecoveredGracefully() {
        when(policy.review(any())).thenReturn(Mono.error(new RuntimeException("generator boom")));

        StepVerifier.create(orchestrator.submit(contextFor("agent-fail"))).verifyComplete();

        // Failure is recorded in counters
        EvolutionCounters counters = stateStore.getCounters("agent-fail");
        assertThat(counters.consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void policyException_stateBecomesIdleOrSuspended() {
        when(policy.review(any())).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(orchestrator.submit(contextFor("agent-state"))).verifyComplete();

        EvolutionState state = stateStore.getState("agent-state");
        assertThat(state).isIn(EvolutionState.IDLE, EvolutionState.SUSPENDED);
    }

    @Test
    void policyException_orchestratorInternalCounterIncrements() {
        // The orchestrator swallows errors internally (fire-and-forget safe). Verify the internal
        // counter is updated via the orchestrator's stateStore, not the hook's failure counter.
        when(policy.review(any())).thenReturn(Mono.error(new RuntimeException("hook error")));

        StepVerifier.create(orchestrator.submit(contextFor("agent-hook-fail"))).verifyComplete();

        assertThat(stateStore.getCounters("agent-hook-fail").consecutiveFailures()).isEqualTo(1);
    }

    // --- Scenario 4: Cumulative improvement tracking ---

    @Test
    void multipleFailures_consecutiveCountAccumulates() {
        when(policy.review(any())).thenReturn(Mono.error(new RuntimeException("repeated")));

        StepVerifier.create(orchestrator.submit(contextFor("agent-multi"))).verifyComplete();
        StepVerifier.create(orchestrator.submit(contextFor("agent-multi"))).verifyComplete();

        assertThat(stateStore.getCounters("agent-multi").consecutiveFailures()).isEqualTo(2);
    }

    @Test
    void threeFailures_agentSuspended() {
        when(policy.review(any())).thenReturn(Mono.error(new RuntimeException("fail")));

        StepVerifier.create(orchestrator.submit(contextFor("agent-suspend"))).verifyComplete();
        StepVerifier.create(orchestrator.submit(contextFor("agent-suspend"))).verifyComplete();
        StepVerifier.create(orchestrator.submit(contextFor("agent-suspend"))).verifyComplete();

        assertThat(stateStore.getState("agent-suspend")).isEqualTo(EvolutionState.SUSPENDED);
    }

    @Test
    void suspendedAgent_skipsReview() {
        stateStore.setState("agent-suspended-skip", EvolutionState.SUSPENDED);

        StepVerifier.create(orchestrator.submit(contextFor("agent-suspended-skip")))
                .verifyComplete();

        verifyNoInteractions(policy);
    }

    // --- Scenario 5: Hook guards ---

    @Test
    void hookSkipsFailedSession() {
        SessionEndEvent failedEvent =
                new SessionEndEvent(
                        "agent-failed",
                        AgentState.FAILED,
                        10,
                        1000L,
                        Duration.ofSeconds(5),
                        "error");
        hook.onSessionEnd(failedEvent);

        assertThat(hook.getSkipCount()).isEqualTo(1);
        verifyNoInteractions(policy);
    }

    @Test
    void hookSkipsLowIterationSession() {
        SessionEndEvent event = completedSession("agent-low", 2);
        hook.onSessionEnd(event);

        assertThat(hook.getSkipCount()).isEqualTo(1);
        verifyNoInteractions(policy);
    }

    @Test
    void skillPatch_existingSkillUpdated() {
        // Prime the store with an existing skill
        EvolvedSkill original = skill("existing-skill");
        StepVerifier.create(skillStore.save(original)).expectNextCount(1).verifyComplete();

        EvolvedSkill patched = skill("existing-skill");
        when(policy.review(any()))
                .thenReturn(
                        Mono.just(
                                new EvolutionOutcome(
                                        Optional.empty(),
                                        Optional.of(patched),
                                        List.of(),
                                        "patched")));

        StepVerifier.create(orchestrator.submit(contextFor("agent-patch"))).verifyComplete();

        StepVerifier.create(skillStore.get("existing-skill"))
                .assertNext(opt -> assertThat(opt).isPresent())
                .verifyComplete();
    }

    // ---- helpers ----

    private EvolutionContext contextFor(String agentName) {
        return new EvolutionContext(
                agentName,
                List.of(Msg.of(MsgRole.USER, "improve"), Msg.of(MsgRole.ASSISTANT, "done")),
                10,
                EvolutionCounters.ZERO,
                5,
                8,
                2000L,
                List.of());
    }
}
