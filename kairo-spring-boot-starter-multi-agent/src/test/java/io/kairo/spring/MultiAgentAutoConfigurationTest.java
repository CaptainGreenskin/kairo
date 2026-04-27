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

import io.kairo.api.team.MessageBus;
import io.kairo.api.team.TeamManager;
import io.kairo.multiagent.team.DefaultTaskDispatchCoordinator;
import io.kairo.multiagent.team.DefaultTeamManager;
import io.kairo.multiagent.team.InProcessMessageBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Tests for {@link MultiAgentAutoConfiguration}. */
class MultiAgentAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MultiAgentAutoConfiguration.class));

    @Test
    void defaultConfigurationCreatesInProcessMessageBus() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(InProcessMessageBus.class);
                    assertThat(context.getBean(InProcessMessageBus.class))
                            .isInstanceOf(MessageBus.class);
                });
    }

    @Test
    void defaultConfigurationCreatesTeamManager() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(DefaultTeamManager.class);
                    assertThat(context.getBean(DefaultTeamManager.class))
                            .isInstanceOf(TeamManager.class);
                });
    }

    @Test
    void defaultConfigurationCreatesTaskDispatchCoordinator() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(DefaultTaskDispatchCoordinator.class);
                });
    }

    @Test
    void whenDisabledByProperty_noBeansCreated() {
        runner.withPropertyValues("kairo.multi-agent.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(MultiAgentAutoConfiguration.class);
                            assertThat(context).doesNotHaveBean(InProcessMessageBus.class);
                            assertThat(context).doesNotHaveBean(DefaultTeamManager.class);
                        });
    }

    @Test
    void whenCustomMessageBusBeanPresent_autoConfigBacksOff() {
        MessageBus customBus =
                new MessageBus() {
                    @Override
                    public reactor.core.publisher.Mono<Void> send(
                            String from, String to, io.kairo.api.message.Msg msg) {
                        return reactor.core.publisher.Mono.empty();
                    }

                    @Override
                    public reactor.core.publisher.Flux<io.kairo.api.message.Msg> receive(
                            String agentId) {
                        return reactor.core.publisher.Flux.empty();
                    }

                    @Override
                    public reactor.core.publisher.Mono<Void> broadcast(
                            String fromAgentId, io.kairo.api.message.Msg message) {
                        return reactor.core.publisher.Mono.empty();
                    }
                };

        runner.withBean("customBus", MessageBus.class, () -> customBus)
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(InProcessMessageBus.class);
                            assertThat(context.getBean(MessageBus.class)).isSameAs(customBus);
                        });
    }

    @Test
    void whenCustomTeamManagerPresent_autoConfigBacksOff() {
        TeamManager custom =
                new TeamManager() {
                    @Override
                    public io.kairo.api.team.Team create(String name) {
                        return null;
                    }

                    @Override
                    public void delete(String name) {}

                    @Override
                    public io.kairo.api.team.Team get(String name) {
                        return null;
                    }

                    @Override
                    public void addAgent(String teamName, io.kairo.api.agent.Agent agent) {}

                    @Override
                    public void removeAgent(String teamName, String agentId) {}
                };

        runner.withBean("customTeamManager", TeamManager.class, () -> custom)
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(DefaultTeamManager.class);
                            assertThat(context.getBean(TeamManager.class)).isSameAs(custom);
                        });
    }

    @Test
    void whenDefaultTeamManagerAbsentFromClasspath_configurationDoesNotLoad() {
        runner.withClassLoader(new FilteredClassLoader(DefaultTeamManager.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(MultiAgentAutoConfiguration.class);
                        });
    }

    @Test
    void enabledTrueExplicitlyCreatesAllBeans() {
        runner.withPropertyValues("kairo.multi-agent.enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(InProcessMessageBus.class);
                            assertThat(context).hasSingleBean(DefaultTeamManager.class);
                            assertThat(context).hasSingleBean(DefaultTaskDispatchCoordinator.class);
                        });
    }
}
