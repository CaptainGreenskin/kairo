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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.model.ModelProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoordinatorConfigTest {

    private static AgentConfig baseConfig() {
        return AgentConfig.builder()
                .name("coordinator")
                .modelProvider(mock(ModelProvider.class))
                .build();
    }

    @Test
    void ofFactoryDoesNotThrow() {
        CoordinatorConfig config = CoordinatorConfig.of(baseConfig());
        assertThat(config).isNotNull();
    }

    @Test
    void defaultMaxConcurrentWorkers() {
        CoordinatorConfig config = CoordinatorConfig.of(baseConfig());
        assertThat(config.maxConcurrentWorkers()).isEqualTo(5);
    }

    @Test
    void defaultRequiresPlanBeforeDispatch() {
        CoordinatorConfig config = CoordinatorConfig.of(baseConfig());
        assertThat(config.requirePlanBeforeDispatch()).isTrue();
    }

    @Test
    void defaultWorkerTemplatesIsEmpty() {
        CoordinatorConfig config = CoordinatorConfig.of(baseConfig());
        assertThat(config.workerTemplates()).isEmpty();
    }

    @Test
    void ofWithWorkerTemplates() {
        AgentConfig worker =
                AgentConfig.builder()
                        .name("worker")
                        .modelProvider(mock(ModelProvider.class))
                        .build();
        CoordinatorConfig config = CoordinatorConfig.of(baseConfig(), List.of(worker));
        assertThat(config.workerTemplates()).hasSize(1);
    }

    @Test
    void builderCustomMaxConcurrentWorkers() {
        CoordinatorConfig config =
                CoordinatorConfig.builder(baseConfig()).maxConcurrentWorkers(10).build();
        assertThat(config.maxConcurrentWorkers()).isEqualTo(10);
    }

    @Test
    void builderNoPlanRequired() {
        CoordinatorConfig config =
                CoordinatorConfig.builder(baseConfig()).requirePlanBeforeDispatch(false).build();
        assertThat(config.requirePlanBeforeDispatch()).isFalse();
    }

    @Test
    void constructorRejectsZeroWorkers() {
        AgentConfig base = baseConfig();
        assertThatThrownBy(() -> new CoordinatorConfig(base, List.of(), 0, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrentWorkers");
    }

    @Test
    void recordEqualityViaOf() {
        AgentConfig base = baseConfig();
        CoordinatorConfig a = CoordinatorConfig.of(base);
        CoordinatorConfig b = CoordinatorConfig.of(base);
        assertThat(a).isEqualTo(b);
    }
}
