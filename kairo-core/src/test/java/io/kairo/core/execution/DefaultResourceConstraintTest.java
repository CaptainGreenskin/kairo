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
package io.kairo.core.execution;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.execution.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("DefaultResourceConstraint")
class DefaultResourceConstraintTest {

    private final DefaultResourceConstraint constraint =
            new DefaultResourceConstraint(10, 100_000L, Duration.ofMinutes(5));

    private ResourceContext ctx(int iteration, long tokens, Duration elapsed) {
        return new ResourceContext(iteration, tokens, elapsed, "test-agent", null);
    }

    @Test
    @DisplayName("validate — iteration within limit returns ok")
    void iterationWithinLimit_returnsOk() {
        StepVerifier.create(constraint.validate(ctx(5, 0, Duration.ZERO)))
                .assertNext(
                        v -> {
                            assertFalse(v.violated());
                            assertEquals("", v.reason());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("validate — iteration at limit returns violated")
    void iterationAtLimit_returnsViolated() {
        StepVerifier.create(constraint.validate(ctx(10, 0, Duration.ZERO)))
                .assertNext(
                        v -> {
                            assertTrue(v.violated());
                            assertTrue(v.reason().contains("max iterations"));
                            assertEquals(10, v.metrics().get("maxIterations"));
                            assertEquals(10, v.metrics().get("currentIteration"));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("validate — tokens within budget returns ok")
    void tokensWithinBudget_returnsOk() {
        StepVerifier.create(constraint.validate(ctx(0, 50_000, Duration.ZERO)))
                .assertNext(v -> assertFalse(v.violated()))
                .verifyComplete();
    }

    @Test
    @DisplayName("validate — tokens exceeding budget returns violated")
    void tokensExceedingBudget_returnsViolated() {
        StepVerifier.create(constraint.validate(ctx(0, 100_000, Duration.ZERO)))
                .assertNext(
                        v -> {
                            assertTrue(v.violated());
                            assertTrue(v.reason().contains("token budget"));
                            assertEquals(100_000L, v.metrics().get("tokenBudget"));
                            assertEquals(100_000L, v.metrics().get("tokensUsed"));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("validate — elapsed within timeout returns ok")
    void elapsedWithinTimeout_returnsOk() {
        StepVerifier.create(constraint.validate(ctx(0, 0, Duration.ofMinutes(3))))
                .assertNext(v -> assertFalse(v.violated()))
                .verifyComplete();
    }

    @Test
    @DisplayName("validate — elapsed exceeding timeout returns violated")
    void elapsedExceedingTimeout_returnsViolated() {
        StepVerifier.create(constraint.validate(ctx(0, 0, Duration.ofMinutes(6))))
                .assertNext(
                        v -> {
                            assertTrue(v.violated());
                            assertTrue(v.reason().contains("timeout"));
                            assertEquals(
                                    Duration.ofMinutes(5).toMillis(), v.metrics().get("timeout"));
                            assertEquals(
                                    Duration.ofMinutes(6).toMillis(), v.metrics().get("elapsed"));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("onViolation — always returns GRACEFUL_EXIT")
    void onViolation_returnsGracefulExit() {
        ResourceValidation violation = ResourceValidation.violated("test", Map.of("key", "value"));
        assertEquals(ResourceAction.GRACEFUL_EXIT, constraint.onViolation(violation));
    }

    @Test
    @DisplayName("validate — checks iteration first (order matters for reporting)")
    void checksIterationFirst() {
        // Both iteration and token limits exceeded — iteration reported first
        StepVerifier.create(constraint.validate(ctx(10, 100_000, Duration.ofMinutes(6))))
                .assertNext(
                        v -> {
                            assertTrue(v.violated());
                            assertTrue(
                                    v.reason().contains("max iterations"),
                                    "Should report iteration violation first, got: " + v.reason());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("constructor — rejects negative maxIterations")
    void constructor_rejectsNegativeMaxIterations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DefaultResourceConstraint(-1, 1000, Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("constructor — rejects negative tokenBudget")
    void constructor_rejectsNegativeTokenBudget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DefaultResourceConstraint(10, -1, Duration.ofMinutes(5)));
    }
}
