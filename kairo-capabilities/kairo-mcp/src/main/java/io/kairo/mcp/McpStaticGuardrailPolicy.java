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

import io.kairo.api.guardrail.GuardrailContext;
import io.kairo.api.guardrail.GuardrailDecision;
import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.GuardrailPolicy;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Static guardrail policy for MCP tool access control.
 *
 * <p>Evaluates MCP-sourced tool calls against per-server {@link McpServerConfig} security settings
 * (security policy, allowed/denied tool lists). Runs as the first policy in the guardrail chain
 * ({@code order = Integer.MIN_VALUE}) to provide fast-path DENY before any user-defined policies.
 *
 * <p>Only acts on {@link GuardrailPhase#PRE_TOOL} phase. Non-MCP tools (those without {@code
 * "mcp.server"} metadata) are silently allowed (skipped).
 *
 * @since v0.7
 * @see McpSecurityPolicy
 * @see McpServerConfig
 */
public class McpStaticGuardrailPolicy implements GuardrailPolicy {

    private static final Logger logger = LoggerFactory.getLogger(McpStaticGuardrailPolicy.class);
    private static final String POLICY_NAME = "McpStaticGuardrailPolicy";

    private final Map<String, McpServerConfig> serverConfigs;

    /**
     * Creates a new MCP static guardrail policy.
     *
     * @param serverConfigs map of server name to config (must not be null)
     */
    public McpStaticGuardrailPolicy(Map<String, McpServerConfig> serverConfigs) {
        this.serverConfigs = Map.copyOf(serverConfigs);
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    @Override
    public String name() {
        return POLICY_NAME;
    }

    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        // Only act on PRE_TOOL phase
        if (context.phase() != GuardrailPhase.PRE_TOOL) {
            return Mono.just(GuardrailDecision.allow(POLICY_NAME));
        }

        // Check if this is an MCP-sourced tool (via metadata)
        String serverName = (String) context.metadata().get("mcp.server");
        if (serverName == null) {
            return Mono.just(GuardrailDecision.allow(POLICY_NAME));
        }

        McpServerConfig config = serverConfigs.get(serverName);
        if (config == null) {
            logger.warn("Denying tool call from unknown MCP server: {}", serverName);
            return Mono.just(
                    GuardrailDecision.deny("Unknown MCP server: " + serverName, POLICY_NAME));
        }

        return evaluateStaticPolicy(context, config);
    }

    private Mono<GuardrailDecision> evaluateStaticPolicy(
            GuardrailContext context, McpServerConfig config) {
        String toolName = context.targetName();

        // 1. DENY_ALL → reject immediately
        if (config.securityPolicy() == McpSecurityPolicy.DENY_ALL) {
            logger.debug(
                    "DENY_ALL policy active for server '{}', blocking tool '{}'",
                    config.name(),
                    toolName);
            return deny("DENY_ALL policy active for server '" + config.name() + "'");
        }

        // 2. Check deniedTools blocklist (applies regardless of policy)
        if (config.deniedTools() != null && config.deniedTools().contains(toolName)) {
            logger.debug("Tool '{}' is in denied list for server '{}'", toolName, config.name());
            return deny(
                    "Tool '" + toolName + "' is in denied list for server '" + config.name() + "'");
        }

        // 3. DENY_SAFE: tool must be in explicit allowlist
        if (config.securityPolicy() == McpSecurityPolicy.DENY_SAFE) {
            if (config.allowedTools() == null || !config.allowedTools().contains(toolName)) {
                String reason =
                        config.allowedTools() == null
                                ? "No allowed tools configured for server '"
                                        + config.name()
                                        + "' (DENY_SAFE)"
                                : "Tool '"
                                        + toolName
                                        + "' not in allowed list for server '"
                                        + config.name()
                                        + "' (DENY_SAFE)";
                logger.debug(
                        "Denying tool '{}' for server '{}' under DENY_SAFE policy",
                        toolName,
                        config.name());
                return deny(reason);
            }
        }

        // 4. ALLOW_ALL or DENY_SAFE with tool in allowlist → allow
        logger.debug("Allowing tool '{}' for server '{}'", toolName, config.name());
        return Mono.just(GuardrailDecision.allow(POLICY_NAME));
    }

    private Mono<GuardrailDecision> deny(String reason) {
        return Mono.just(GuardrailDecision.deny(reason, POLICY_NAME));
    }
}
