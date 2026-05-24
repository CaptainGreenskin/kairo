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
package io.kairo.mcp;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.guardrail.GuardrailContext;
import io.kairo.api.guardrail.GuardrailDecision;
import io.kairo.api.guardrail.GuardrailPayload;
import io.kairo.api.guardrail.GuardrailPhase;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class McpStaticGuardrailPolicyTest {

    private McpStaticGuardrailPolicy createPolicy(Map<String, McpServerConfig> configs) {
        return new McpStaticGuardrailPolicy(configs);
    }

    private McpServerConfig serverConfig(
            McpSecurityPolicy policy, Set<String> allowed, Set<String> denied) {
        return McpServerConfig.builder()
                .name("test-server")
                .transportType(McpServerConfig.TransportType.STDIO)
                .command(java.util.List.of("cmd"))
                .securityPolicy(policy)
                .allowedTools(allowed)
                .deniedTools(denied != null ? denied : Set.of())
                .build();
    }

    private GuardrailContext preToolContext(String toolName, Map<String, Object> metadata) {
        return new GuardrailContext(
                GuardrailPhase.PRE_TOOL,
                "agent",
                toolName,
                new GuardrailPayload.ToolInput(toolName, Map.of()),
                metadata);
    }

    // ---- order and name ----

    @Test
    void orderIsMinValue() {
        var policy = createPolicy(Map.of());
        assertEquals(Integer.MIN_VALUE, policy.order());
    }

    @Test
    void nameIsCorrect() {
        var policy = createPolicy(Map.of());
        assertEquals("McpStaticGuardrailPolicy", policy.name());
    }

    // ---- Non-PRE_TOOL phase returns ALLOW (skip) ----

    @Test
    void nonPreToolPhaseReturnsAllow() {
        var policy = createPolicy(Map.of());
        var context =
                new GuardrailContext(
                        GuardrailPhase.POST_TOOL,
                        "agent",
                        "tool",
                        new GuardrailPayload.ToolInput("tool", Map.of()),
                        Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> {
                            assertEquals(GuardrailDecision.Action.ALLOW, decision.action());
                        })
                .verifyComplete();
    }

    @Test
    void preModelPhaseReturnsAllow() {
        var policy = createPolicy(Map.of());
        var context =
                new GuardrailContext(
                        GuardrailPhase.PRE_MODEL,
                        "agent",
                        "model",
                        new GuardrailPayload.ToolInput("model", Map.of()),
                        Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> assertEquals(GuardrailDecision.Action.ALLOW, decision.action()))
                .verifyComplete();
    }

    // ---- Non-MCP tool (no mcp.server metadata) returns ALLOW (skip) ----

    @Test
    void nonMcpToolReturnsAllow() {
        var policy = createPolicy(Map.of());
        var context = preToolContext("regular_tool", Map.of());

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> assertEquals(GuardrailDecision.Action.ALLOW, decision.action()))
                .verifyComplete();
    }

    // ---- Unknown server name → DENY ----

    @Test
    void unknownServerNameDenies() {
        var policy = createPolicy(Map.of());
        var context = preToolContext("tool", Map.of("mcp.server", "unknown-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> {
                            assertEquals(GuardrailDecision.Action.DENY, decision.action());
                            assertTrue(decision.reason().contains("Unknown MCP server"));
                        })
                .verifyComplete();
    }

    // ---- DENY_ALL blocks all tools ----

    @Test
    void denyAllBlocksAllTools() {
        var config = serverConfig(McpSecurityPolicy.DENY_ALL, null, Set.of());
        var policy = createPolicy(Map.of("test-server", config));
        var context = preToolContext("any_tool", Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> {
                            assertEquals(GuardrailDecision.Action.DENY, decision.action());
                            assertTrue(decision.reason().contains("DENY_ALL"));
                        })
                .verifyComplete();
    }

    // ---- DENY_SAFE with allowedTools permits listed tools, blocks unlisted ----

    @Test
    void denySafeWithAllowedToolsPermitsListed() {
        var config =
                serverConfig(
                        McpSecurityPolicy.DENY_SAFE, Set.of("read_file", "list_dir"), Set.of());
        var policy = createPolicy(Map.of("test-server", config));
        var context = preToolContext("read_file", Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> assertEquals(GuardrailDecision.Action.ALLOW, decision.action()))
                .verifyComplete();
    }

    @Test
    void denySafeWithAllowedToolsBlocksUnlisted() {
        var config = serverConfig(McpSecurityPolicy.DENY_SAFE, Set.of("read_file"), Set.of());
        var policy = createPolicy(Map.of("test-server", config));
        var context = preToolContext("delete_file", Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> {
                            assertEquals(GuardrailDecision.Action.DENY, decision.action());
                            assertTrue(decision.reason().contains("not in allowed list"));
                        })
                .verifyComplete();
    }

    // ---- DENY_SAFE with no allowedTools blocks all ----

    @Test
    void denySafeWithNoAllowedToolsBlocksAll() {
        var config = serverConfig(McpSecurityPolicy.DENY_SAFE, null, Set.of());
        var policy = createPolicy(Map.of("test-server", config));
        var context = preToolContext("any_tool", Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> {
                            assertEquals(GuardrailDecision.Action.DENY, decision.action());
                            assertTrue(decision.reason().contains("No allowed tools configured"));
                        })
                .verifyComplete();
    }

    // ---- ALLOW_ALL permits everything ----

    @Test
    void allowAllPermitsEverything() {
        var config = serverConfig(McpSecurityPolicy.ALLOW_ALL, null, Set.of());
        var policy = createPolicy(Map.of("test-server", config));
        var context = preToolContext("any_tool", Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> assertEquals(GuardrailDecision.Action.ALLOW, decision.action()))
                .verifyComplete();
    }

    // ---- deniedTools blocks specific tool even with ALLOW_ALL ----

    @Test
    void deniedToolsBlocksEvenWithAllowAll() {
        var config = serverConfig(McpSecurityPolicy.ALLOW_ALL, null, Set.of("dangerous_tool"));
        var policy = createPolicy(Map.of("test-server", config));
        var context = preToolContext("dangerous_tool", Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> {
                            assertEquals(GuardrailDecision.Action.DENY, decision.action());
                            assertTrue(decision.reason().contains("denied list"));
                        })
                .verifyComplete();
    }

    @Test
    void deniedToolsDoesNotBlockOtherToolsWithAllowAll() {
        var config = serverConfig(McpSecurityPolicy.ALLOW_ALL, null, Set.of("dangerous_tool"));
        var policy = createPolicy(Map.of("test-server", config));
        var context = preToolContext("safe_tool", Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> assertEquals(GuardrailDecision.Action.ALLOW, decision.action()))
                .verifyComplete();
    }

    // ---- Multiple servers ----

    @Test
    void multipleServersEvaluateCorrectConfig() {
        var allowAllConfig = serverConfig(McpSecurityPolicy.ALLOW_ALL, null, Set.of());
        var denyAllConfig =
                McpServerConfig.builder()
                        .name("restricted")
                        .transportType(McpServerConfig.TransportType.STDIO)
                        .command(java.util.List.of("cmd"))
                        .securityPolicy(McpSecurityPolicy.DENY_ALL)
                        .build();

        var policy =
                createPolicy(Map.of("test-server", allowAllConfig, "restricted", denyAllConfig));

        // test-server allows
        var context1 = preToolContext("tool", Map.of("mcp.server", "test-server"));
        StepVerifier.create(policy.evaluate(context1))
                .assertNext(
                        decision -> assertEquals(GuardrailDecision.Action.ALLOW, decision.action()))
                .verifyComplete();

        // restricted denies
        var context2 = preToolContext("tool", Map.of("mcp.server", "restricted"));
        StepVerifier.create(policy.evaluate(context2))
                .assertNext(
                        decision -> assertEquals(GuardrailDecision.Action.DENY, decision.action()))
                .verifyComplete();
    }

    // ---- Policy name in decision ----

    @Test
    void decisionContainsPolicyName() {
        var config = serverConfig(McpSecurityPolicy.DENY_ALL, null, Set.of());
        var policy = createPolicy(Map.of("test-server", config));
        var context = preToolContext("tool", Map.of("mcp.server", "test-server"));

        StepVerifier.create(policy.evaluate(context))
                .assertNext(
                        decision -> assertEquals("McpStaticGuardrailPolicy", decision.policyName()))
                .verifyComplete();
    }
}
