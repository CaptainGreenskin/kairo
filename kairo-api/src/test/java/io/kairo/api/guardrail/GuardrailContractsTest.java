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
package io.kairo.api.guardrail;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuardrailContractsTest {

    // --- GuardrailPhase ---

    @Test
    void phaseHasExactlyFourValues() {
        GuardrailPhase[] values = GuardrailPhase.values();
        assertEquals(4, values.length);
        assertNotNull(GuardrailPhase.PRE_MODEL);
        assertNotNull(GuardrailPhase.POST_MODEL);
        assertNotNull(GuardrailPhase.PRE_TOOL);
        assertNotNull(GuardrailPhase.POST_TOOL);
    }

    // --- GuardrailDecision.Action ---

    @Test
    void actionHasExactlyFourValues() {
        GuardrailDecision.Action[] values = GuardrailDecision.Action.values();
        assertEquals(4, values.length);
        assertNotNull(GuardrailDecision.Action.ALLOW);
        assertNotNull(GuardrailDecision.Action.DENY);
        assertNotNull(GuardrailDecision.Action.MODIFY);
        assertNotNull(GuardrailDecision.Action.WARN);
    }

    // --- GuardrailDecision factory methods ---

    @Test
    void allowDecision() {
        GuardrailDecision d = GuardrailDecision.allow("test-policy");
        assertEquals(GuardrailDecision.Action.ALLOW, d.action());
        assertEquals("allowed", d.reason());
        assertEquals("test-policy", d.policyName());
        assertNull(d.modifiedPayload());
    }

    @Test
    void denyDecision() {
        GuardrailDecision d = GuardrailDecision.deny("blocked", "content-filter");
        assertEquals(GuardrailDecision.Action.DENY, d.action());
        assertEquals("blocked", d.reason());
        assertEquals("content-filter", d.policyName());
        assertNull(d.modifiedPayload());
    }

    @Test
    void modifyDecision() {
        GuardrailPayload.ToolInput payload = new GuardrailPayload.ToolInput("echo", Map.of("x", 1));
        GuardrailDecision d = GuardrailDecision.modify(payload, "redacted", "pii-filter");
        assertEquals(GuardrailDecision.Action.MODIFY, d.action());
        assertEquals("redacted", d.reason());
        assertEquals("pii-filter", d.policyName());
        assertSame(payload, d.modifiedPayload());
    }

    @Test
    void warnDecision() {
        GuardrailDecision d = GuardrailDecision.warn("suspicious", "anomaly-detector");
        assertEquals(GuardrailDecision.Action.WARN, d.action());
        assertEquals("suspicious", d.reason());
        assertEquals("anomaly-detector", d.policyName());
        assertNull(d.modifiedPayload());
    }

    // --- GuardrailContext ---

    @Test
    void contextRecordAccessors() {
        GuardrailPayload.ToolInput payload = new GuardrailPayload.ToolInput("read", Map.of());
        Map<String, Object> meta = Map.of("traceId", "abc-123");
        GuardrailContext ctx =
                new GuardrailContext(GuardrailPhase.PRE_TOOL, "my-agent", "read", payload, meta);

        assertEquals(GuardrailPhase.PRE_TOOL, ctx.phase());
        assertEquals("my-agent", ctx.agentName());
        assertEquals("read", ctx.targetName());
        assertSame(payload, ctx.payload());
        assertEquals(meta, ctx.metadata());
    }

    // --- GuardrailPayload sealed variants ---

    @Test
    void modelInputVariant() {
        Msg msg = Msg.of(MsgRole.USER, "hello");
        GuardrailPayload.ModelInput mi = new GuardrailPayload.ModelInput(List.of(msg), null);
        assertInstanceOf(GuardrailPayload.class, mi);
        assertEquals(1, mi.messages().size());
        assertNull(mi.config());
    }

    @Test
    void modelOutputVariant() {
        ModelResponse resp =
                new ModelResponse(
                        "r1",
                        List.of(new Content.TextContent("hi")),
                        new ModelResponse.Usage(10, 5, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");
        GuardrailPayload.ModelOutput mo = new GuardrailPayload.ModelOutput(resp);
        assertInstanceOf(GuardrailPayload.class, mo);
        assertEquals(resp, mo.response());
    }

    @Test
    void toolInputVariant() {
        GuardrailPayload.ToolInput ti =
                new GuardrailPayload.ToolInput("calculator", Map.of("expr", "1+1"));
        assertInstanceOf(GuardrailPayload.class, ti);
        assertEquals("calculator", ti.toolName());
        assertEquals(Map.of("expr", "1+1"), ti.args());
    }

    @Test
    void toolOutputVariant() {
        ToolResult result = new ToolResult("tu-1", "2", false, Map.of());
        GuardrailPayload.ToolOutput to = new GuardrailPayload.ToolOutput("calculator", result);
        assertInstanceOf(GuardrailPayload.class, to);
        assertEquals("calculator", to.toolName());
        assertEquals(result, to.result());
    }

    @Test
    void payloadIsSealedWithExactlyFourPermittedSubtypes() {
        assertTrue(GuardrailPayload.class.isSealed());
        Class<?>[] permitted = GuardrailPayload.class.getPermittedSubclasses();
        assertNotNull(permitted);
        assertEquals(4, permitted.length);
    }
}
