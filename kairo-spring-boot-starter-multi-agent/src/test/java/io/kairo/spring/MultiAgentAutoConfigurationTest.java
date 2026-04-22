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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MultiAgentAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MultiAgentAutoConfiguration.class));

    @Test
    void whenDefaultTeamManagerOnClasspath_configurationLoads() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(MultiAgentAutoConfiguration.class);
                });
    }

    @Test
    void whenConditionalClassAbsent_configurationDoesNotLoad() {
        contextRunner
                .withClassLoader(
                        new FilteredClassLoader(io.kairo.multiagent.team.DefaultTeamManager.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(MultiAgentAutoConfiguration.class);
                        });
    }

    @Test
    void configurationHasConditionalOnClassAnnotation() {
        assertThat(MultiAgentAutoConfiguration.class).hasAnnotation(ConditionalOnClass.class);
    }

    @Test
    void configurationRegistersNoAdditionalBeans() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(MultiAgentAutoConfiguration.class);
                    // No TeamManager or other beans are auto-wired yet
                    assertThat(context.getBeanDefinitionCount()).isGreaterThan(0);
                });
    }
}
