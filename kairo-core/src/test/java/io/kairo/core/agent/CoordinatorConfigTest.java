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

    private static final AgentConfig BASE =
            AgentConfig.builder()
                    .name("coordinator")
                    .modelProvider(mock(ModelProvider.class))
                    .build();

    @Test
    void ofDefaultsHave5WorkersAndRequiresPlan() {
        CoordinatorConfig config = CoordinatorConfig.of(BASE);
        assertThat(config.maxConcurrentWorkers()).isEqualTo(5);
        assertThat(config.requirePlanBeforeDispatch()).isTrue();
    }

    @Test
    void ofDefaultsHasEmptyWorkerTemplates() {
        CoordinatorConfig config = CoordinatorConfig.of(BASE);
        assertThat(config.workerTemplates()).isEmpty();
    }

    @Test
    void ofStoresBaseConfig() {
        CoordinatorConfig config = CoordinatorConfig.of(BASE);
        assertThat(config.baseConfig()).isSameAs(BASE);
    }

    @Test
    void ofWithWorkerTemplatesStoresThem() {
        AgentConfig worker =
                AgentConfig.builder()
                        .name("worker")
                        .modelProvider(mock(ModelProvider.class))
                        .build();
        CoordinatorConfig config = CoordinatorConfig.of(BASE, List.of(worker));
        assertThat(config.workerTemplates()).containsExactly(worker);
    }

    @Test
    void builderOverridesMaxWorkers() {
        CoordinatorConfig config = CoordinatorConfig.builder(BASE).maxConcurrentWorkers(10).build();
        assertThat(config.maxConcurrentWorkers()).isEqualTo(10);
    }

    @Test
    void builderOverridesRequirePlan() {
        CoordinatorConfig config =
                CoordinatorConfig.builder(BASE).requirePlanBeforeDispatch(false).build();
        assertThat(config.requirePlanBeforeDispatch()).isFalse();
    }

    @Test
    void zeroMaxWorkersThrows() {
        assertThatThrownBy(() -> CoordinatorConfig.builder(BASE).maxConcurrentWorkers(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeMaxWorkersThrows() {
        assertThatThrownBy(() -> CoordinatorConfig.builder(BASE).maxConcurrentWorkers(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
