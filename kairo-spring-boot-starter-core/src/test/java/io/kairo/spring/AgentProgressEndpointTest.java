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

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.agent.DefaultReActAgent;
import io.kairo.core.agent.ProgressSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tests for {@link AgentActuatorAutoConfiguration.AgentProgressEndpoint}. */
class AgentProgressEndpointTest {

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
                    .withConfiguration(
                            AutoConfigurations.of(
                                    AgentRuntimeAutoConfiguration.class,
                                    AgentActuatorAutoConfiguration.class))
                    .withBean("modelProvider", ModelProvider.class, () -> NOOP_PROVIDER);

    @Test
    void progressEndpointBeanCreated() {
        runner.run(
                context -> {
                    assertThat(context)
                            .hasSingleBean(
                                    AgentActuatorAutoConfiguration.AgentProgressEndpoint.class);
                });
    }

    @Test
    void progressEndpointAnnotatedWithCorrectId() {
        Endpoint annotation =
                AgentActuatorAutoConfiguration.AgentProgressEndpoint.class.getAnnotation(
                        Endpoint.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.id()).isEqualTo("agent-progress");
    }

    @Test
    void progressReturnsSnapshotFieldsForDefaultReActAgent() {
        runner.run(
                context -> {
                    Agent agent = context.getBean(Agent.class);
                    assertThat(agent).isInstanceOf(DefaultReActAgent.class);

                    AgentActuatorAutoConfiguration.AgentProgressEndpoint endpoint =
                            context.getBean(
                                    AgentActuatorAutoConfiguration.AgentProgressEndpoint.class);
                    Map<String, Object> result = endpoint.progress();

                    assertThat(result).containsKey("currentIteration");
                    assertThat(result).containsKey("maxIterations");
                    assertThat(result).containsKey("percentage");
                    assertThat(result).containsKey("currentActivity");
                    assertThat(result).containsKey("elapsedMs");
                    assertThat(result).containsKey("toolCallsCount");
                    assertThat(result).containsKey("tokensUsed");
                });
    }

    @Test
    void initialProgressShowsZeroIteration() {
        runner.run(
                context -> {
                    AgentActuatorAutoConfiguration.AgentProgressEndpoint endpoint =
                            context.getBean(
                                    AgentActuatorAutoConfiguration.AgentProgressEndpoint.class);
                    Map<String, Object> result = endpoint.progress();

                    assertThat(result.get("currentIteration")).isEqualTo(0);
                    assertThat(result.get("percentage")).isEqualTo(0);
                    assertThat(result.get("currentActivity")).isEqualTo("Initializing");
                });
    }

    @Test
    void progressReturnsUnavailableForNonDefaultAgent() {
        Agent nonDefaultAgent =
                new Agent() {
                    @Override
                    public String id() {
                        return "fake";
                    }

                    @Override
                    public String name() {
                        return "fake";
                    }

                    @Override
                    public io.kairo.api.agent.AgentState state() {
                        return io.kairo.api.agent.AgentState.IDLE;
                    }

                    @Override
                    public Mono<Msg> call(Msg input) {
                        return Mono.empty();
                    }

                    @Override
                    public void interrupt() {}
                };

        AgentActuatorAutoConfiguration.AgentProgressEndpoint endpoint =
                new AgentActuatorAutoConfiguration.AgentProgressEndpoint(nonDefaultAgent);
        Map<String, Object> result = endpoint.progress();

        assertThat(result).containsEntry("status", "unavailable");
    }

    @Test
    void defaultReActAgentGetProgressReturnsSnapshot() {
        runner.run(
                context -> {
                    Agent agent = context.getBean(Agent.class);
                    assertThat(agent).isInstanceOf(DefaultReActAgent.class);

                    DefaultReActAgent dra = (DefaultReActAgent) agent;
                    ProgressSnapshot snap = dra.getProgress();

                    assertThat(snap).isNotNull();
                    assertThat(snap.currentIteration()).isZero();
                    assertThat(snap.currentActivity()).isEqualTo("Initializing");
                });
    }
}
