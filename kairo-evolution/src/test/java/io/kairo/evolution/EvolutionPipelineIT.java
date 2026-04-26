package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.evolution.*;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.skill.InMemoryEvolvedSkillStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end integration tests for the full evolution pipeline: Trigger → Policy Review → Skill
 * Store Update → State Tracking
 */
@Tag("integration")
class EvolutionPipelineIT {

    private InMemoryEvolvedSkillStore skillStore;
    private InMemoryEvolutionRuntimeStateStore stateStore;
    private EvolutionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        skillStore = new InMemoryEvolvedSkillStore();
        stateStore = new InMemoryEvolutionRuntimeStateStore();
        stateMachine = new EvolutionStateMachine(3);
    }

    private EvolutionPipelineOrchestrator orchestratorWith(EvolutionPolicy policy) {
        return new EvolutionPipelineOrchestrator(policy, skillStore, stateMachine, stateStore);
    }

    private EvolutionContext contextFor(String agentName) {
        return new EvolutionContext(
                agentName,
                List.of(
                        Msg.of(MsgRole.USER, "implement feature X"),
                        Msg.of(MsgRole.ASSISTANT, "Done.")),
                15,
                EvolutionCounters.ZERO,
                10,
                12,
                8000L,
                List.of());
    }

    private EvolvedSkill newSkill(String name, String version) {
        return new EvolvedSkill(
                name,
                version,
                "desc",
                "instructions",
                "test",
                Set.of("java"),
                SkillTrustLevel.DRAFT,
                null,
                Instant.now(),
                Instant.now(),
                0);
    }

    // --- Scenario 1: Normal evolution — policy creates a new skill ---

    @Test
    void normalEvolution_newSkillPersistedToStore() {
        EvolvedSkill skill = newSkill("refactor-helper", "1.0.0");
        EvolutionOutcome outcome =
                new EvolutionOutcome(
                        Optional.of(skill), Optional.empty(), List.of(), "New skill created");

        EvolutionPolicy policy = ctx -> Mono.just(outcome);
        EvolutionPipelineOrchestrator orchestrator = orchestratorWith(policy);

        StepVerifier.create(orchestrator.submit(contextFor("agent-A"))).verifyComplete();

        Optional<EvolvedSkill> stored = skillStore.get("refactor-helper").block();
        assertThat(stored).isPresent();
        assertThat(stored.get().version()).isEqualTo("1.0.0");
    }

    @Test
    void normalEvolution_patchExistingSkill() {
        EvolvedSkill v1 = newSkill("code-review", "1.0.0");
        skillStore.save(v1).block();

        EvolvedSkill v2 = newSkill("code-review", "1.1.0");
        EvolutionOutcome outcome =
                new EvolutionOutcome(Optional.empty(), Optional.of(v2), List.of(), "Skill patched");

        EvolutionPolicy policy = ctx -> Mono.just(outcome);
        EvolutionPipelineOrchestrator orchestrator = orchestratorWith(policy);

        StepVerifier.create(orchestrator.submit(contextFor("agent-B"))).verifyComplete();

        Optional<EvolvedSkill> stored = skillStore.get("code-review").block();
        assertThat(stored).isPresent();
        assertThat(stored.get().version()).isEqualTo("1.1.0");
    }

    // --- Scenario 2: Policy returns empty outcome — no skill changes ---

    @Test
    void emptyOutcome_noSkillChanges() {
        EvolutionPolicy policy = ctx -> Mono.just(EvolutionOutcome.empty());
        EvolutionPipelineOrchestrator orchestrator = orchestratorWith(policy);

        StepVerifier.create(orchestrator.submit(contextFor("agent-C"))).verifyComplete();

        long skillCount = skillStore.list().count().block();
        assertThat(skillCount).isEqualTo(0);
    }

    @Test
    void emptyOutcome_stateRemainsActive() {
        EvolutionPolicy policy = ctx -> Mono.just(EvolutionOutcome.empty());
        EvolutionPipelineOrchestrator orchestrator = orchestratorWith(policy);

        StepVerifier.create(orchestrator.submit(contextFor("agent-D"))).verifyComplete();

        EvolutionState state = stateStore.getState("agent-D");
        assertThat(state).isNotEqualTo(EvolutionState.SUSPENDED);
    }

    // --- Scenario 3: Policy error — failure counter increments, store unchanged ---

    @Test
    void policyError_failureCounterIncremented() {
        EvolutionPolicy policy = ctx -> Mono.error(new RuntimeException("policy failure"));
        EvolutionPipelineOrchestrator orchestrator = orchestratorWith(policy);

        StepVerifier.create(orchestrator.submit(contextFor("agent-E"))).verifyComplete();

        EvolutionCounters counters = stateStore.getCounters("agent-E");
        assertThat(counters.consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void policyError_noSkillPersistedOnFailure() {
        EvolutionPolicy policy = ctx -> Mono.error(new RuntimeException("policy failure"));
        EvolutionPipelineOrchestrator orchestrator = orchestratorWith(policy);

        StepVerifier.create(orchestrator.submit(contextFor("agent-F"))).verifyComplete();

        long skillCount = skillStore.list().count().block();
        assertThat(skillCount).isEqualTo(0);
    }

    @Test
    void repeatedPolicyErrors_suspendAfterMaxFailures() {
        EvolutionPolicy policy = ctx -> Mono.error(new RuntimeException("repeated failure"));
        EvolutionPipelineOrchestrator orchestrator = orchestratorWith(policy);

        // maxFailures = 3 (set in stateMachine constructor)
        for (int i = 0; i < 3; i++) {
            orchestrator.submit(contextFor("agent-G")).block();
        }

        EvolutionState state = stateStore.getState("agent-G");
        assertThat(state).isEqualTo(EvolutionState.SUSPENDED);
    }

    @Test
    void suspendedAgent_policyNotCalledAfterSuspension() {
        EvolutionPolicy policy = mock(EvolutionPolicy.class);
        when(policy.review(any())).thenReturn(Mono.error(new RuntimeException("fail")));
        EvolutionPipelineOrchestrator orchestrator = orchestratorWith(policy);

        // Drive to suspension
        for (int i = 0; i < 3; i++) {
            orchestrator.submit(contextFor("agent-H")).block();
        }
        reset(policy);

        // Subsequent submit should be a no-op
        StepVerifier.create(orchestrator.submit(contextFor("agent-H"))).verifyComplete();

        verifyNoInteractions(policy);
    }
}
