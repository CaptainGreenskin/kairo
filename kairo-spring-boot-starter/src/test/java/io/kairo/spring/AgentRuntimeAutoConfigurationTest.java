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
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.message.Msg;
import io.kairo.api.middleware.Middleware;
import io.kairo.api.middleware.MiddlewareChain;
import io.kairo.api.middleware.MiddlewareContext;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.agent.DefaultReActAgent;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tests for {@link AgentRuntimeAutoConfiguration}. */
class AgentRuntimeAutoConfigurationTest {

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

    @Test
    void defaultAgentUsesConfiguredModelName() {
        runner.withPropertyValues("kairo.model.model-name=test-model-name")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(Agent.class);
                            Agent agent = context.getBean(Agent.class);
                            assertThat(agent).isInstanceOf(DefaultReActAgent.class);

                            Field configField = DefaultReActAgent.class.getDeclaredField("config");
                            configField.setAccessible(true);
                            AgentConfig config = (AgentConfig) configField.get(agent);
                            assertThat(config.modelName()).isEqualTo("test-model-name");
                        });
    }

    @Test
    void defaultAgentUsesDefaultModelNameWhenNotConfigured() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(Agent.class);
                    Agent agent = context.getBean(Agent.class);

                    Field configField = DefaultReActAgent.class.getDeclaredField("config");
                    configField.setAccessible(true);
                    AgentConfig config = (AgentConfig) configField.get(agent);
                    assertThat(config.modelName()).isEqualTo("claude-sonnet-4-20250514");
                });
    }

    @Test
    void middlewareBeansAreCollectedIntoDefaultAgent() {
        Middleware testMiddleware =
                new Middleware() {
                    @Override
                    public String name() {
                        return "test-mw";
                    }

                    @Override
                    public Mono<MiddlewareContext> handle(
                            MiddlewareContext ctx, MiddlewareChain chain) {
                        return chain.next(ctx);
                    }
                };

        runner.withBean("testMiddleware", Middleware.class, () -> testMiddleware)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(Agent.class);
                            Agent agent = context.getBean(Agent.class);

                            Field configField = DefaultReActAgent.class.getDeclaredField("config");
                            configField.setAccessible(true);
                            AgentConfig config = (AgentConfig) configField.get(agent);
                            assertThat(config.middlewares()).hasSize(1);
                            assertThat(config.middlewares().get(0).name()).isEqualTo("test-mw");
                        });
    }

    @Test
    void agentCreatedWithoutMiddlewareWhenNoneRegistered() {
        runner.run(
                context -> {
                    Agent agent = context.getBean(Agent.class);
                    Field configField = DefaultReActAgent.class.getDeclaredField("config");
                    configField.setAccessible(true);
                    AgentConfig config = (AgentConfig) configField.get(agent);
                    assertThat(config.middlewares()).isEmpty();
                });
    }

    @Test
    void agentConfiguredWithCustomIterations() {
        runner.withPropertyValues(
                        "kairo.model.model-name=test",
                        "kairo.agent.max-iterations=25",
                        "kairo.agent.token-budget=100000")
                .run(
                        context -> {
                            Agent agent = context.getBean(Agent.class);
                            Field configField = DefaultReActAgent.class.getDeclaredField("config");
                            configField.setAccessible(true);
                            AgentConfig config = (AgentConfig) configField.get(agent);
                            assertThat(config.maxIterations()).isEqualTo(25);
                            assertThat(config.tokenBudget()).isEqualTo(100000);
                        });
    }
}
