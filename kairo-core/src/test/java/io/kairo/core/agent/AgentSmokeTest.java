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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Smoke test for the full Agent chain: Builder → Agent → ModelProvider → Response.
 */
@Tag("smoke")
class AgentSmokeTest {

    @Test
    void fullChainBuilderToResponse() {
        // Mock ModelProvider returning a fixed text response "Done"
        ModelProvider mockProvider = mock(ModelProvider.class);
        ModelResponse response = new ModelResponse(
                "resp-smoke",
                List.of(new Content.TextContent("Done")),
                new ModelResponse.Usage(5, 10, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "mock-model");
        when(mockProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(response));

        // Build agent via AgentBuilder
        Agent agent = AgentBuilder.create()
                .name("smoke")
                .model(mockProvider)
                .modelName("mock-model")
                .maxIterations(1)
                .build();

        assertNotNull(agent);
        assertEquals("smoke", agent.name());

        // Call agent with a simple user message
        Msg userMsg = Msg.of(MsgRole.USER, "Hello");

        StepVerifier.create(agent.call(userMsg))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertTrue(result.text().contains("Done"),
                            "Expected response to contain 'Done', got: " + result.text());
                })
                .verifyComplete();
    }
}
