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
package io.kairo.core.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.kairo.api.model.ModelProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Validation tests for {@link AgentBuilder} — verifies all guard clauses in {@code build()}, {@code
 * buildConfig()}, and individual setters.
 */
class AgentBuilderValidationTest {

    private final ModelProvider mockProvider = mock(ModelProvider.class);

    // ===== 1. Name validation =====

    @Test
    void throwsWhenNameIsNull() {
        NullPointerException ex =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                AgentBuilder.create()
                                        .model(mockProvider)
                                        .modelName("test-model")
                                        .build());
        assertTrue(ex.getMessage().contains("Agent name must not be null"));
    }

    @Test
    void throwsWhenNameIsBlank() {
        // AgentBuilder.name("") does not throw — buildConfig() only checks null via
        // Objects.requireNonNull. Blank name passes validation and builds successfully.
        assertDoesNotThrow(
                () ->
                        AgentBuilder.create()
                                .name("")
                                .model(mockProvider)
                                .modelName("test-model")
                                .build());
    }

    // ===== 2. ModelName validation =====

    @Test
    void throwsWhenModelNameMissing() {
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> AgentBuilder.create().name("test").model(mockProvider).build());
        assertTrue(
                ex.getMessage().contains("modelName"),
                "Expected message to contain 'modelName', got: " + ex.getMessage());
    }

    @Test
    void throwsWhenModelNameIsBlank() {
        // The modelName() setter validates eagerly
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                AgentBuilder.create()
                                        .name("test")
                                        .model(mockProvider)
                                        .modelName("")
                                        .build());
        assertTrue(ex.getMessage().contains("modelName cannot be null or blank"));
    }

    // ===== 3. MaxIterations validation =====

    @Test
    void throwsWhenMaxIterationsZero() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                AgentBuilder.create()
                                        .name("test")
                                        .model(mockProvider)
                                        .modelName("test-model")
                                        .maxIterations(0)
                                        .build());
        assertTrue(ex.getMessage().contains("maxIterations must be positive"));
    }

    @Test
    void throwsWhenMaxIterationsNegative() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                AgentBuilder.create()
                                        .name("test")
                                        .model(mockProvider)
                                        .modelName("test-model")
                                        .maxIterations(-1)
                                        .build());
        assertTrue(ex.getMessage().contains("maxIterations must be positive"));
    }

    // ===== 4. TokenBudget validation =====

    @Test
    void throwsWhenTokenBudgetNegative() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                AgentBuilder.create()
                                        .name("test")
                                        .model(mockProvider)
                                        .modelName("test-model")
                                        .tokenBudget(-1)
                                        .build());
        assertTrue(ex.getMessage().contains("tokenBudget must be positive"));
    }

    @Test
    void throwsWhenTokenBudgetZero() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                AgentBuilder.create()
                                        .name("test")
                                        .model(mockProvider)
                                        .modelName("test-model")
                                        .tokenBudget(0)
                                        .build());
        assertTrue(ex.getMessage().contains("tokenBudget must be positive"));
    }

    // ===== 5. Hook null handling =====

    @Test
    void acceptsNullHookGracefully() {
        // hook(null) silently ignores the null — does not throw
        assertDoesNotThrow(
                () ->
                        AgentBuilder.create()
                                .name("test")
                                .model(mockProvider)
                                .modelName("test-model")
                                .hook(null)
                                .build());
    }

    // ===== 6. Successful build =====

    @Test
    void buildsSuccessfullyWithAllValidParams() {
        assertDoesNotThrow(
                () -> {
                    var agent =
                            AgentBuilder.create()
                                    .name("full-agent")
                                    .model(mockProvider)
                                    .modelName("gpt-4o")
                                    .systemPrompt("You are helpful.")
                                    .maxIterations(25)
                                    .timeout(Duration.ofMinutes(5))
                                    .tokenBudget(100_000)
                                    .hook(new Object())
                                    .build();
                    assertNotNull(agent);
                    assertEquals("full-agent", agent.name());
                });
    }

    // ===== 7. ModelName null via setter =====

    @Test
    void throwsWhenModelNameIsNullViaSetter() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> AgentBuilder.create().modelName(null));
        assertTrue(ex.getMessage().contains("modelName cannot be null or blank"));
    }

    // ===== 8. buildCoordinator validation =====

    @Test
    void buildCoordinatorThrowsWhenNameMissing() {
        // buildCoordinator() calls buildConfig() which validates name
        assertThrows(
                NullPointerException.class,
                () ->
                        AgentBuilder.create()
                                .model(mockProvider)
                                .modelName("test-model")
                                .buildCoordinator());
    }

    @Test
    void buildCoordinatorThrowsWhenModelNameMissing() {
        assertThrows(
                IllegalStateException.class,
                () -> AgentBuilder.create().name("coord").model(mockProvider).buildCoordinator());
    }
}
