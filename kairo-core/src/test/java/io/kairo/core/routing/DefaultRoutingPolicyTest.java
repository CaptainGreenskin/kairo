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
package io.kairo.core.routing;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.routing.RoutingContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultRoutingPolicyTest {

    private final DefaultRoutingPolicy policy = new DefaultRoutingPolicy();

    @Test
    void returnsConfiguredProviderName() {
        ModelConfig config = ModelConfig.builder().model("claude-sonnet-4-20250514").build();
        RoutingContext ctx = new RoutingContext("agent", List.of(), config, null);

        StepVerifier.create(policy.selectProvider(ctx))
                .assertNext(
                        decision ->
                                assertEquals("claude-sonnet-4-20250514", decision.providerName()))
                .verifyComplete();
    }

    @Test
    void returnsDefaultNoRoutingRationale() {
        ModelConfig config = ModelConfig.builder().model("gpt-4o").build();
        RoutingContext ctx = new RoutingContext("agent", List.of(), config, null);

        StepVerifier.create(policy.selectProvider(ctx))
                .assertNext(decision -> assertEquals("default-no-routing", decision.rationale()))
                .verifyComplete();
    }

    @Test
    void returnsNullEstimatedCost() {
        ModelConfig config = ModelConfig.builder().model("test-model").build();
        RoutingContext ctx = new RoutingContext("agent", List.of(), config, null);

        StepVerifier.create(policy.selectProvider(ctx))
                .assertNext(decision -> assertNull(decision.estimatedCost()))
                .verifyComplete();
    }

    @Test
    void handlesNullCostBudgetInContext() {
        ModelConfig config = ModelConfig.builder().model("test-model").build();
        Msg msg = Msg.of(MsgRole.USER, "Hello");
        RoutingContext ctx = new RoutingContext("my-agent", List.of(msg), config, null);

        StepVerifier.create(policy.selectProvider(ctx))
                .assertNext(
                        decision -> {
                            assertEquals("test-model", decision.providerName());
                            assertEquals("default-no-routing", decision.rationale());
                            assertNull(decision.estimatedCost());
                        })
                .verifyComplete();
    }

    @Test
    void handlesCostBudgetInContext() {
        ModelConfig config = ModelConfig.builder().model("test-model").costBudget(10.0).build();
        RoutingContext ctx = new RoutingContext("my-agent", List.of(), config, 10.0);

        StepVerifier.create(policy.selectProvider(ctx))
                .assertNext(
                        decision -> {
                            // Default policy ignores cost budget
                            assertEquals("test-model", decision.providerName());
                            assertNull(decision.estimatedCost());
                        })
                .verifyComplete();
    }
}
