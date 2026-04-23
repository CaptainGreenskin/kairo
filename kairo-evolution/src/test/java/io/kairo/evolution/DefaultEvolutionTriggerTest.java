package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolutionContext;
import io.kairo.api.evolution.EvolutionCounters;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultEvolutionTriggerTest {

    private DefaultEvolutionTrigger trigger;

    @BeforeEach
    void setUp() {
        trigger = new DefaultEvolutionTrigger();
    }

    private EvolutionContext context(
            int toolLoopIterations, int turnsSinceMemory, int skillThreshold, int memoryThreshold) {
        EvolutionCounters counters = new EvolutionCounters(turnsSinceMemory, toolLoopIterations, 0);
        return new EvolutionContext(
                "agent",
                List.of(Msg.of(MsgRole.USER, "hi")),
                10,
                counters,
                memoryThreshold,
                skillThreshold,
                1000L,
                List.of());
    }

    @Test
    void shouldReviewSkillWhenAboveThreshold() {
        EvolutionContext ctx = context(10, 0, 8, 5);
        assertThat(trigger.shouldReviewSkill(ctx)).isTrue();
    }

    @Test
    void shouldNotReviewSkillWhenBelowThreshold() {
        EvolutionContext ctx = context(3, 0, 8, 5);
        assertThat(trigger.shouldReviewSkill(ctx)).isFalse();
    }

    @Test
    void shouldReviewMemoryWhenAboveThreshold() {
        EvolutionContext ctx = context(0, 6, 8, 5);
        assertThat(trigger.shouldReviewMemory(ctx)).isTrue();
    }

    @Test
    void shouldNotReviewMemoryWhenBelowThreshold() {
        EvolutionContext ctx = context(0, 2, 8, 5);
        assertThat(trigger.shouldReviewMemory(ctx)).isFalse();
    }

    @Test
    void reasonReturnsDefault() {
        assertThat(trigger.reason()).isEqualTo("default-threshold-trigger");
    }
}
