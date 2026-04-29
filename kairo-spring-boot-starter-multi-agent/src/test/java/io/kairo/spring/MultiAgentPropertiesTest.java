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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Tests for {@link MultiAgentProperties} binding and getter/setter correctness. */
class MultiAgentPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MultiAgentProperties.class)
    static class TestConfig {}

    @Test
    void defaultValues() {
        runner.run(
                context -> {
                    MultiAgentProperties props = context.getBean(MultiAgentProperties.class);
                    assertThat(props.isEnabled()).isTrue();
                });
    }

    @Test
    void enabledCanBeSetToFalse() {
        runner.withPropertyValues("kairo.multi-agent.enabled=false")
                .run(
                        context -> {
                            MultiAgentProperties props =
                                    context.getBean(MultiAgentProperties.class);
                            assertThat(props.isEnabled()).isFalse();
                        });
    }

    @Test
    void enabledCanBeSetToTrue() {
        runner.withPropertyValues("kairo.multi-agent.enabled=true")
                .run(
                        context -> {
                            MultiAgentProperties props =
                                    context.getBean(MultiAgentProperties.class);
                            assertThat(props.isEnabled()).isTrue();
                        });
    }

    @Test
    void setterGetterWorkCorrectly() {
        MultiAgentProperties props = new MultiAgentProperties();
        assertThat(props.isEnabled()).isTrue();

        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();

        props.setEnabled(true);
        assertThat(props.isEnabled()).isTrue();
    }
}
