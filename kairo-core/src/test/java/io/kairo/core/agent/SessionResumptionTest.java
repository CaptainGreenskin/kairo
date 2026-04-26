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
import reactor.test.StepVerifier;

class SessionResumptionTest {

    private static AgentConfig CONFIG_NO_SESSION;
    private static AgentConfig CONFIG_NO_MEMORY_STORE;

    @BeforeAll
    static void setup() {
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
        CONFIG_NO_SESSION = AgentConfig.builder().name("test-agent").modelProvider(stub).build();
        CONFIG_NO_MEMORY_STORE =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(stub)
                        .sessionId("sess-123")
                        .build();
    }

    @Test
    void noSessionId_returnsEmptyMono() {
        SessionResumption sr = new SessionResumption(CONFIG_NO_SESSION, null);
        StepVerifier.create(sr.loadSessionIfConfigured()).verifyComplete();
    }

    @Test
    void noMemoryStore_returnsEmptyMono() {
        SessionResumption sr = new SessionResumption(CONFIG_NO_MEMORY_STORE, null);
        StepVerifier.create(sr.loadSessionIfConfigured()).verifyComplete();
    }

    @Test
    void noSessionId_returnsNonNull() {
        SessionResumption sr = new SessionResumption(CONFIG_NO_SESSION, null);
        assertThat(sr.loadSessionIfConfigured()).isNotNull();
    }

    @Test
    void noMemoryStore_returnsNonNull() {
        SessionResumption sr = new SessionResumption(CONFIG_NO_MEMORY_STORE, null);
        assertThat(sr.loadSessionIfConfigured()).isNotNull();
    }
}
