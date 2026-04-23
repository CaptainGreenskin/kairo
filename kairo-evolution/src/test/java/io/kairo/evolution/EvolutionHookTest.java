package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentState;
import io.kairo.api.evolution.EvolutionContext;
import io.kairo.api.evolution.EvolutionOutcome;
import io.kairo.api.evolution.EvolutionPolicy;
import io.kairo.api.evolution.EvolutionTrigger;
import io.kairo.api.hook.SessionEndEvent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class EvolutionHookTest {

    private EvolutionTrigger trigger;
    private InMemoryEvolvedSkillStore skillStore;
    private EvolutionPolicy policy;
    private EvolutionPipelineOrchestrator orchestrator;
    private EvolutionHook hook;

    @BeforeEach
    void setUp() {
        trigger = mock(EvolutionTrigger.class);
        skillStore = new InMemoryEvolvedSkillStore();
        policy = mock(EvolutionPolicy.class);
        when(policy.review(any())).thenReturn(Mono.just(EvolutionOutcome.empty()));

        EvolutionStateMachine stateMachine = new EvolutionStateMachine(3);
        InMemoryEvolutionRuntimeStateStore stateStore = new InMemoryEvolutionRuntimeStateStore();
        orchestrator =
                new EvolutionPipelineOrchestrator(policy, skillStore, stateMachine, stateStore);
        hook = new EvolutionHook(trigger, skillStore, orchestrator);
    }

    @Test
    void skipsNonCompletedSession() {
        SessionEndEvent event =
                new SessionEndEvent(
                        "agent",
                        AgentState.FAILED,
                        10,
                        5000L,
                        Duration.ofMinutes(1),
                        "error",
                        () -> List.of(Msg.of(MsgRole.USER, "hello")));

        hook.onSessionEnd(event);
        verifyNoInteractions(policy);
        assertThat(hook.getSkipCount()).isEqualTo(1);
    }

    @Test
    void skipsLowIterationSession() {
        SessionEndEvent event =
                new SessionEndEvent(
                        "agent",
                        AgentState.COMPLETED,
                        1,
                        500L,
                        Duration.ofSeconds(5),
                        null,
                        () -> List.of(Msg.of(MsgRole.USER, "hello")));

        hook.onSessionEnd(event);
        verifyNoInteractions(policy);
        assertThat(hook.getSkipCount()).isEqualTo(1);
    }

    @Test
    void skipsNullHistorySupplier() {
        SessionEndEvent event =
                new SessionEndEvent(
                        "agent",
                        AgentState.COMPLETED,
                        10,
                        5000L,
                        Duration.ofMinutes(1),
                        null,
                        null);

        hook.onSessionEnd(event);
        verifyNoInteractions(policy);
        assertThat(hook.getSkipCount()).isEqualTo(1);
    }

    @Test
    void triggersOnCompletedHighIterationSession() {
        Supplier<List<Msg>> supplier =
                () ->
                        List.of(
                                Msg.of(MsgRole.USER, "do something"),
                                Msg.of(MsgRole.ASSISTANT, "done"));
        SessionEndEvent event =
                new SessionEndEvent(
                        "agent",
                        AgentState.COMPLETED,
                        10,
                        5000L,
                        Duration.ofMinutes(1),
                        null,
                        supplier);

        hook.onSessionEnd(event);
        // The hook calls orchestrator.submit() which calls policy.review()
        // Give a moment for async subscribe
        verify(policy, timeout(2000)).review(any(EvolutionContext.class));
        // successCount increments on the onComplete callback of the async subscribe
        assertThat(hook.getSkipCount()).isEqualTo(0);
    }

    @Test
    void skipCountAccumulatesAcrossMultipleSkips() {
        SessionEndEvent failedEvent =
                new SessionEndEvent(
                        "agent",
                        AgentState.FAILED,
                        10,
                        5000L,
                        Duration.ofMinutes(1),
                        "err",
                        () -> List.of(Msg.of(MsgRole.USER, "hello")));

        SessionEndEvent lowIterEvent =
                new SessionEndEvent(
                        "agent",
                        AgentState.COMPLETED,
                        1,
                        500L,
                        Duration.ofSeconds(5),
                        null,
                        () -> List.of(Msg.of(MsgRole.USER, "hello")));

        hook.onSessionEnd(failedEvent);
        hook.onSessionEnd(lowIterEvent);
        assertThat(hook.getSkipCount()).isEqualTo(2);
        assertThat(hook.getSuccessCount()).isEqualTo(0);
        assertThat(hook.getFailureCount()).isEqualTo(0);
    }

    @Test
    void supplierIsLazilyInvoked() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        Supplier<List<Msg>> trackingSupplier =
                () -> {
                    supplierCalled.set(true);
                    return List.of(Msg.of(MsgRole.USER, "hi"));
                };

        // FAILED state → should skip before calling supplier
        SessionEndEvent failedEvent =
                new SessionEndEvent(
                        "agent",
                        AgentState.FAILED,
                        10,
                        5000L,
                        Duration.ofMinutes(1),
                        "err",
                        trackingSupplier);
        hook.onSessionEnd(failedEvent);
        assertThat(supplierCalled.get()).isFalse();

        // COMPLETED + enough iterations → should call supplier
        SessionEndEvent successEvent =
                new SessionEndEvent(
                        "agent",
                        AgentState.COMPLETED,
                        10,
                        5000L,
                        Duration.ofMinutes(1),
                        null,
                        trackingSupplier);
        hook.onSessionEnd(successEvent);
        assertThat(supplierCalled.get()).isTrue();
    }
}
