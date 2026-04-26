package io.kairo.api.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ExecutionEventTypeBoundaryTest {

    @Test
    void executionDomainMustNotContainEvolutionLifecycleEvents() {
        boolean containsEvolutionEvents =
                Arrays.stream(ExecutionEventType.values())
                        .map(Enum::name)
                        .anyMatch(
                                name -> name.startsWith("SKILL_") || name.startsWith("EVOLUTION_"));
        assertFalse(
                containsEvolutionEvents,
                "ExecutionEventType must stay in execution domain and exclude evolution lifecycle events");
    }
}
