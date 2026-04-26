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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CoordinatorConfigTest {

    private static AgentConfig BASE_CONFIG;

    @BeforeAll
    static void setUpBaseConfig() {
        ModelProvider stub =
                new ModelProvider() {
                    @Override
                    public String name() {
                        return "stub";
                    }

                    @Override
                    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
                        return Mono.empty();
                    }

                    @Override
                    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
                        return Flux.empty();
                    }
                };
        BASE_CONFIG = AgentConfig.builder().name("test-agent").modelProvider(stub).build();
    }

    @Test
    void ofDefaultMaxConcurrentWorkersFive() {
        CoordinatorConfig config = CoordinatorConfig.of(BASE_CONFIG);
        assertEquals(5, config.maxConcurrentWorkers());
    }

    @Test
    void ofDefaultRequirePlanBeforeDispatchTrue() {
        CoordinatorConfig config = CoordinatorConfig.of(BASE_CONFIG);
        assertTrue(config.requirePlanBeforeDispatch());
    }

    @Test
    void ofDefaultWorkerTemplatesEmpty() {
        CoordinatorConfig config = CoordinatorConfig.of(BASE_CONFIG);
        assertTrue(config.workerTemplates().isEmpty());
    }

    @Test
    void ofWithWorkerTemplatesPreservesList() {
        CoordinatorConfig config = CoordinatorConfig.of(BASE_CONFIG, List.of(BASE_CONFIG));
        assertEquals(1, config.workerTemplates().size());
    }

    @Test
    void maxConcurrentWorkersZeroThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CoordinatorConfig(BASE_CONFIG, List.of(), 0, true));
    }

    @Test
    void maxConcurrentWorkersNegativeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CoordinatorConfig(BASE_CONFIG, List.of(), -1, true));
    }

    @Test
    void builderOverridesMaxConcurrentWorkers() {
        CoordinatorConfig config =
                CoordinatorConfig.builder(BASE_CONFIG).maxConcurrentWorkers(10).build();
        assertEquals(10, config.maxConcurrentWorkers());
    }

    @Test
    void builderSetRequirePlanBeforeDispatchFalse() {
        CoordinatorConfig config =
                CoordinatorConfig.builder(BASE_CONFIG).requirePlanBeforeDispatch(false).build();
        assertFalse(config.requirePlanBeforeDispatch());
    }

    @Test
    void builderSetsWorkerTemplates() {
        CoordinatorConfig config =
                CoordinatorConfig.builder(BASE_CONFIG)
                        .workerTemplates(List.of(BASE_CONFIG))
                        .build();
        assertEquals(1, config.workerTemplates().size());
    }

    @Test
    void recordEqualityWhenSameFields() {
        CoordinatorConfig a = CoordinatorConfig.of(BASE_CONFIG);
        CoordinatorConfig b = CoordinatorConfig.of(BASE_CONFIG);
        assertEquals(a, b);
    }

    @Test
    void recordHashCodeConsistentWithEquals() {
        CoordinatorConfig a = CoordinatorConfig.of(BASE_CONFIG);
        CoordinatorConfig b = CoordinatorConfig.of(BASE_CONFIG);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
