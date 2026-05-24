package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolutionCounters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvolutionRuntimeStateStoreTest {

    private InMemoryEvolutionRuntimeStateStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryEvolutionRuntimeStateStore();
    }

    @Test
    void defaultStateIsIdle() {
        assertThat(store.getState("unknown-agent")).isEqualTo(EvolutionState.IDLE);
    }

    @Test
    void setAndGetState() {
        store.setState("agent-1", EvolutionState.REVIEWING);
        assertThat(store.getState("agent-1")).isEqualTo(EvolutionState.REVIEWING);
    }

    @Test
    void defaultCountersAreZero() {
        EvolutionCounters counters = store.getCounters("unknown-agent");
        assertThat(counters).isEqualTo(EvolutionCounters.ZERO);
        assertThat(counters.turnSinceLastMemoryReview()).isZero();
        assertThat(counters.toolLoopIterationsSinceLastSkillReview()).isZero();
        assertThat(counters.consecutiveFailures()).isZero();
    }

    @Test
    void setAndGetCounters() {
        EvolutionCounters c = new EvolutionCounters(5, 10, 2);
        store.setCounters("agent-1", c);
        EvolutionCounters got = store.getCounters("agent-1");
        assertThat(got.turnSinceLastMemoryReview()).isEqualTo(5);
        assertThat(got.toolLoopIterationsSinceLastSkillReview()).isEqualTo(10);
        assertThat(got.consecutiveFailures()).isEqualTo(2);
    }

    @Test
    void resetClearsStateAndCounters() {
        store.setState("agent-1", EvolutionState.SUSPENDED);
        store.setCounters("agent-1", new EvolutionCounters(5, 10, 3));

        store.reset("agent-1");

        assertThat(store.getState("agent-1")).isEqualTo(EvolutionState.IDLE);
        assertThat(store.getCounters("agent-1")).isEqualTo(EvolutionCounters.ZERO);
    }
}
