package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.evolution.*;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EvolutionPipelineOrchestratorTest {

    private EvolutionPolicy policy;
    private InMemoryEvolvedSkillStore skillStore;
    private InMemoryEvolutionRuntimeStateStore stateStore;
    private EvolutionPipelineOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        policy = mock(EvolutionPolicy.class);
        skillStore = new InMemoryEvolvedSkillStore();
        stateStore = new InMemoryEvolutionRuntimeStateStore();
        EvolutionStateMachine stateMachine = new EvolutionStateMachine(3);
        orchestrator =
                new EvolutionPipelineOrchestrator(policy, skillStore, stateMachine, stateStore);
    }

    private EvolutionContext context(String agentName) {
        return new EvolutionContext(
                agentName,
                List.of(Msg.of(MsgRole.USER, "hello")),
                10,
                EvolutionCounters.ZERO,
                5,
                8,
                1000L,
                List.of());
    }

    @Test
    void submitExecutesPipelineAsync() {
        when(policy.review(any())).thenReturn(Mono.just(EvolutionOutcome.empty()));

        StepVerifier.create(orchestrator.submit(context("agent-1"))).verifyComplete();

        verify(policy).review(any());
    }

    @Test
    void suspendedAgentSkipsReview() {
        stateStore.setState("agent-2", EvolutionState.SUSPENDED);

        StepVerifier.create(orchestrator.submit(context("agent-2"))).verifyComplete();

        verifyNoInteractions(policy);
    }

    @Test
    void failureIncrementsCounter() {
        when(policy.review(any())).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(orchestrator.submit(context("agent-3"))).verifyComplete();

        EvolutionCounters counters = stateStore.getCounters("agent-3");
        assertThat(counters.consecutiveFailures()).isEqualTo(1);
    }
}
