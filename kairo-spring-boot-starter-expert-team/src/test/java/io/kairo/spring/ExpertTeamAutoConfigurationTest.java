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

import io.kairo.api.team.TeamCoordinator;
import io.kairo.expertteam.AgentEvaluationStrategy;
import io.kairo.expertteam.ExpertTeamCoordinator;
import io.kairo.expertteam.SimpleEvaluationStrategy;
import io.kairo.expertteam.internal.DefaultPlanner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ExpertTeamAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ExpertTeamAutoConfiguration.class));

    @Test
    void defaultOff_noBeansRegistered() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(ExpertTeamAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(TeamCoordinator.class);
                    assertThat(context).doesNotHaveBean(SimpleEvaluationStrategy.class);
                });
    }

    @Test
    void enabledTrue_wiresCoordinatorAndStrategies() {
        contextRunner
                .withPropertyValues("kairo.expert-team.enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ExpertTeamAutoConfiguration.class);
                            assertThat(context).hasSingleBean(SimpleEvaluationStrategy.class);
                            assertThat(context).hasSingleBean(AgentEvaluationStrategy.class);
                            assertThat(context).hasSingleBean(DefaultPlanner.class);
                            assertThat(context).hasSingleBean(TeamCoordinator.class);
                            assertThat(context.getBean(TeamCoordinator.class))
                                    .isInstanceOf(ExpertTeamCoordinator.class);
                        });
    }

    @Test
    void whenExpertTeamClassAbsent_configurationDoesNotLoad() {
        contextRunner
                .withPropertyValues("kairo.expert-team.enabled=true")
                .withClassLoader(new FilteredClassLoader(ExpertTeamCoordinator.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(ExpertTeamAutoConfiguration.class);
                        });
    }

    @Test
    void propertiesBindWithCustomValues() {
        contextRunner
                .withPropertyValues(
                        "kairo.expert-team.enabled=true",
                        "kairo.expert-team.default-risk-profile=HIGH",
                        "kairo.expert-team.default-max-feedback-rounds=5",
                        "kairo.expert-team.default-team-timeout=2m",
                        "kairo.expert-team.default-evaluator-preference=SIMPLE")
                .run(
                        context -> {
                            ExpertTeamProperties props =
                                    context.getBean(ExpertTeamProperties.class);
                            assertThat(props.isEnabled()).isTrue();
                            assertThat(props.getDefaultRiskProfile().name()).isEqualTo("HIGH");
                            assertThat(props.getDefaultMaxFeedbackRounds()).isEqualTo(5);
                            assertThat(props.getDefaultTeamTimeout().toMinutes()).isEqualTo(2);
                            assertThat(props.getDefaultEvaluatorPreference().name())
                                    .isEqualTo("SIMPLE");
                        });
    }

    @Test
    void userProvidedTeamCoordinatorWins() {
        TeamCoordinator userCoordinator = (request, team) -> reactor.core.publisher.Mono.empty();
        contextRunner
                .withPropertyValues("kairo.expert-team.enabled=true")
                .withBean("userCoordinator", TeamCoordinator.class, () -> userCoordinator)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(TeamCoordinator.class);
                            assertThat(context.getBean(TeamCoordinator.class))
                                    .isSameAs(userCoordinator);
                        });
    }
}
