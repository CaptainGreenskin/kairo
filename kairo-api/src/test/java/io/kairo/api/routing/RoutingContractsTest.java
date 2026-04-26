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
package io.kairo.api.routing;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingContractsTest {

    @Test
    void routingContextConstructionWithAllFields() {
        ModelConfig config = ModelConfig.builder().model("claude-sonnet-4-20250514").build();
        Msg msg = Msg.of(MsgRole.USER, "Hello");
        RoutingContext ctx = new RoutingContext("test-agent", List.of(msg), config, 5.0);

        assertEquals("test-agent", ctx.agentName());
        assertEquals(1, ctx.messages().size());
        assertEquals("Hello", ctx.messages().get(0).text());
        assertSame(config, ctx.config());
        assertEquals(5.0, ctx.costBudget());
    }

    @Test
    void routingContextWithNullCostBudget() {
        ModelConfig config = ModelConfig.builder().model("test-model").build();
        RoutingContext ctx = new RoutingContext("agent", List.of(), config, null);

        assertNull(ctx.costBudget());
    }

    @Test
    void routingDecisionConstructionAndAccessors() {
        RoutingDecision decision = new RoutingDecision("anthropic", "cheapest-option", 0.05);

        assertEquals("anthropic", decision.providerName());
        assertEquals("cheapest-option", decision.rationale());
        assertEquals(0.05, decision.estimatedCost());
    }

    @Test
    void routingDecisionWithNullEstimatedCost() {
        RoutingDecision decision = new RoutingDecision("openai", "default", null);

        assertEquals("openai", decision.providerName());
        assertEquals("default", decision.rationale());
        assertNull(decision.estimatedCost());
    }

    @Test
    void routingDecisionEquality() {
        RoutingDecision d1 = new RoutingDecision("provider", "reason", 1.0);
        RoutingDecision d2 = new RoutingDecision("provider", "reason", 1.0);
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void modelConfigCostBudgetViaBuilder() {
        ModelConfig config = ModelConfig.builder().model("test-model").costBudget(1.5).build();

        assertEquals(1.5, config.costBudget());
    }

    @Test
    void modelConfigCostBudgetDefaultsToNull() {
        ModelConfig config = ModelConfig.builder().model("test-model").build();

        assertNull(config.costBudget());
    }
}
