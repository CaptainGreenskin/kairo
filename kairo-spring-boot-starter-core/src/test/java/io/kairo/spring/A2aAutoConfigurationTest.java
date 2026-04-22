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

import io.kairo.api.a2a.A2aClient;
import io.kairo.api.a2a.AgentCardResolver;
import io.kairo.api.message.Msg;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tests for {@link A2aAutoConfiguration}. */
class A2aAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(A2aAutoConfiguration.class));

    @Test
    void defaultBeansAreCreated() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(AgentCardResolver.class);
                    assertThat(context).hasSingleBean(A2aClient.class);
                    assertThat(context.getBean(AgentCardResolver.class).getClass().getSimpleName())
                            .isEqualTo("InProcessAgentCardResolver");
                    assertThat(context.getBean(A2aClient.class).getClass().getSimpleName())
                            .isEqualTo("InProcessA2aClient");
                });
    }

    @Test
    void userDefinedAgentCardResolverTakesPrecedence() {
        AgentCardResolver custom =
                new AgentCardResolver() {
                    @Override
                    public Optional<io.kairo.api.a2a.AgentCard> resolve(String agentId) {
                        return Optional.empty();
                    }

                    @Override
                    public List<io.kairo.api.a2a.AgentCard> discover(Set<String> requiredTags) {
                        return List.of();
                    }

                    @Override
                    public List<io.kairo.api.a2a.AgentCard> listAll() {
                        return List.of();
                    }

                    @Override
                    public void register(io.kairo.api.a2a.AgentCard card) {}

                    @Override
                    public void unregister(String agentId) {}
                };
        runner.withBean(AgentCardResolver.class, () -> custom)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(AgentCardResolver.class);
                            assertThat(context.getBean(AgentCardResolver.class)).isSameAs(custom);
                        });
    }

    @Test
    void userDefinedA2aClientTakesPrecedence() {
        A2aClient custom =
                new A2aClient() {
                    @Override
                    public Mono<Msg> send(String targetAgentId, Msg message) {
                        return Mono.empty();
                    }

                    @Override
                    public Flux<Msg> stream(String targetAgentId, Msg message) {
                        return Flux.empty();
                    }
                };
        runner.withBean(A2aClient.class, () -> custom)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(A2aClient.class);
                            assertThat(context.getBean(A2aClient.class)).isSameAs(custom);
                        });
    }

    @Test
    void disabledViaProperty() {
        runner.withPropertyValues("kairo.a2a.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(AgentCardResolver.class);
                            assertThat(context).doesNotHaveBean(A2aClient.class);
                        });
    }

    @Test
    void enabledByDefault() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(AgentCardResolver.class);
                    assertThat(context).hasSingleBean(A2aClient.class);
                });
    }
}
