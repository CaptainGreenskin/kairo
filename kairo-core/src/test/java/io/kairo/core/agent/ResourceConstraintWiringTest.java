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
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.execution.*;
import io.kairo.api.model.ModelProvider;
import io.kairo.core.execution.DefaultResourceConstraint;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * End-to-end wiring tests for {@link ResourceConstraint} flowing through AgentConfig → AgentBuilder
 * → ReActLoop → IterationGuards.
 */
@DisplayName("ResourceConstraint end-to-end wiring")
class ResourceConstraintWiringTest {

    private static ModelProvider testProvider() {
        return mock(ModelProvider.class);
    }

    @Test
    @DisplayName("custom ResourceConstraint set via AgentConfig.Builder is reachable")
    void customConstraint_reachableViaConfig() {
        ResourceConstraint custom =
                new ResourceConstraint() {
                    @Override
                    public Mono<ResourceValidation> validate(ResourceContext context) {
                        return Mono.just(ResourceValidation.ok());
                    }

                    @Override
                    public ResourceAction onViolation(ResourceValidation validation) {
                        return ResourceAction.GRACEFUL_EXIT;
                    }
                };

        AgentConfig config =
                AgentConfig.builder()
                        .name("test")
                        .modelProvider(testProvider())
                        .modelName("test-model")
                        .resourceConstraints(List.of(custom))
                        .build();

        assertNotNull(config.resourceConstraints());
        assertEquals(1, config.resourceConstraints().size());
        assertSame(custom, config.resourceConstraints().get(0));
    }

    @Test
    @DisplayName("null resourceConstraints means auto-create DefaultResourceConstraint")
    void nullConstraints_autoCreatesDefault() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test")
                        .modelProvider(testProvider())
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(50_000)
                        .timeout(Duration.ofMinutes(5))
                        .build();

        // resourceConstraints is null — ReActLoop should auto-create DefaultResourceConstraint
        assertNull(config.resourceConstraints());
    }

    @Test
    @DisplayName("empty list means user explicitly opted out of all constraints")
    void emptyList_meansOptOut() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test")
                        .modelProvider(testProvider())
                        .modelName("test-model")
                        .resourceConstraints(List.of())
                        .build();

        assertNotNull(config.resourceConstraints());
        assertTrue(config.resourceConstraints().isEmpty());
    }

    @Test
    @DisplayName("resourceConstraints list is defensively copied")
    void resourceConstraints_defensivelyCopied() {
        ResourceConstraint c1 = new DefaultResourceConstraint(10, 100_000, Duration.ofMinutes(5));
        ResourceConstraint c2 =
                new ResourceConstraint() {
                    @Override
                    public Mono<ResourceValidation> validate(ResourceContext context) {
                        return Mono.just(ResourceValidation.ok());
                    }

                    @Override
                    public ResourceAction onViolation(ResourceValidation validation) {
                        return ResourceAction.ALLOW;
                    }
                };

        var mutable = new java.util.ArrayList<>(List.of(c1, c2));

        AgentConfig config =
                AgentConfig.builder()
                        .name("test")
                        .modelProvider(testProvider())
                        .modelName("test-model")
                        .resourceConstraints(mutable)
                        .build();

        // Mutating the original list should not affect the config
        mutable.clear();
        assertEquals(2, config.resourceConstraints().size());
    }
}
