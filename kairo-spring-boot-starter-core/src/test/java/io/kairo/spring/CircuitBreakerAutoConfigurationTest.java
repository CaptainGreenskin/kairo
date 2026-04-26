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
package io.kairo.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.model.ModelCircuitBreaker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Tests for {@link ModelCircuitBreaker} auto-configuration in {@link
 * AgentRuntimeAutoConfiguration}.
 *
 * <p>Every test uses {@link ApplicationContextRunner} so no real Spring context is started.
 */
class CircuitBreakerAutoConfigurationTest {

    private static final ModelProvider NOOP_PROVIDER =
            new ModelProvider() {
                @Override
                public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
                    return Mono.empty();
                }

                @Override
                public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
                    return Flux.empty();
                }

                @Override
                public String name() {
                    return "noop";
                }
            };

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(AgentRuntimeAutoConfiguration.class))
                    .withBean("modelProvider", ModelProvider.class, () -> NOOP_PROVIDER);

    // ---- 1. Bean created by default (enabled=true is the default) ----

    @Test
    void testCircuitBreakerBeanCreatedByDefault() {
        runner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ModelCircuitBreaker.class);
                });
    }

    // ---- 2. Bean NOT created when disabled ----

    @Test
    void testCircuitBreakerBeanNotCreatedWhenDisabled() {
        runner.withPropertyValues("kairo.model.circuit-breaker.enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(ModelCircuitBreaker.class);
                        });
    }

    // ---- 3. Custom failure threshold applied ----

    @Test
    void testCustomFailureThresholdApplied() {
        runner.withPropertyValues("kairo.model.circuit-breaker.failure-threshold=10")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            ModelCircuitBreaker breaker =
                                    context.getBean(ModelCircuitBreaker.class);

                            // Record 9 failures — should still be CLOSED
                            for (int i = 0; i < 9; i++) {
                                breaker.recordFailure();
                            }
                            assertThat(breaker.getState())
                                    .isEqualTo(ModelCircuitBreaker.State.CLOSED);

                            // 10th failure should trip the breaker to OPEN
                            breaker.recordFailure();
                            assertThat(breaker.getState())
                                    .isEqualTo(ModelCircuitBreaker.State.OPEN);
                        });
    }

    // ---- 4. Custom reset timeout applied ----

    @Test
    void testCustomResetTimeoutApplied() {
        runner.withPropertyValues("kairo.model.circuit-breaker.reset-timeout=120s")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            ModelCircuitBreaker breaker =
                                    context.getBean(ModelCircuitBreaker.class);

                            // Trip the breaker to OPEN using default threshold (5)
                            for (int i = 0; i < 5; i++) {
                                breaker.recordFailure();
                            }
                            assertThat(breaker.getState())
                                    .isEqualTo(ModelCircuitBreaker.State.OPEN);

                            // Immediately after opening, allowCall() should return false
                            // because 120s has not elapsed yet
                            assertThat(breaker.allowCall()).isFalse();
                        });
    }

    // ---- 5. Default values used (threshold=5, timeout=60s) ----

    @Test
    void testDefaultValuesUsed() {
        runner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    ModelCircuitBreaker breaker = context.getBean(ModelCircuitBreaker.class);

                    // Verify default threshold is 5:
                    // 4 failures should keep it CLOSED
                    for (int i = 0; i < 4; i++) {
                        breaker.recordFailure();
                    }
                    assertThat(breaker.getState()).isEqualTo(ModelCircuitBreaker.State.CLOSED);

                    // 5th failure triggers OPEN
                    breaker.recordFailure();
                    assertThat(breaker.getState()).isEqualTo(ModelCircuitBreaker.State.OPEN);

                    // Verify default timeout is 60s: allowCall() should return false
                    // immediately (60s has not elapsed)
                    assertThat(breaker.allowCall()).isFalse();
                });
    }
}
