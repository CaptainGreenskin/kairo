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
package io.kairo.core.tool;

import io.kairo.api.guardrail.*;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.ToolSideEffect;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Framework-level safety guardrail that enforces workspace boundaries and blocks catastrophic
 * commands at the {@link GuardrailPhase#PRE_TOOL} phase.
 *
 * <p>This policy runs with order {@code -100} to execute before any business-level guardrails.
 *
 * <p>Enforcement rules:
 *
 * <ul>
 *   <li>{@link ToolSideEffect#WRITE} and {@link ToolSideEffect#SYSTEM_CHANGE} tools: workspace
 *       boundary checks on all extracted file paths.
 *   <li>{@link ToolSideEffect#SYSTEM_CHANGE} tools: Tier 1 catastrophic command denial via {@link
 *       CommandSafetyPolicy}.
 * </ul>
 *
 * @since 1.3.0
 */
public class ToolSafetyGuardrailPolicy implements GuardrailPolicy {

    private final CommandSafetyPolicy commandPolicy;
    private final ToolRegistry registry;

    public ToolSafetyGuardrailPolicy(ToolRegistry registry) {
        this.commandPolicy = CommandSafetyPolicy.instance();
        this.registry = registry;
    }

    /** Constructor for testing — allows injecting a mock {@link CommandSafetyPolicy}. */
    public ToolSafetyGuardrailPolicy(ToolRegistry registry, CommandSafetyPolicy commandPolicy) {
        this.commandPolicy = commandPolicy;
        this.registry = registry;
    }

    @Override
    public int order() {
        return -100; // Run FIRST, before business guardrails
    }

    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        if (context.phase() != GuardrailPhase.PRE_TOOL) {
            return Mono.just(GuardrailDecision.allow(name()));
        }

        String toolName = context.targetName();

        // Resolve side-effect from registry; unknown tools default to strictest classification
        ToolSideEffect sideEffect =
                registry.get(toolName)
                        .map(ToolDefinition::sideEffect)
                        .orElse(ToolSideEffect.SYSTEM_CHANGE);

        // Extract args from the typed payload
        Map<String, Object> args = extractArgs(context);

        // WRITE and SYSTEM_CHANGE tools: workspace boundary enforcement
        if (sideEffect == ToolSideEffect.WRITE || sideEffect == ToolSideEffect.SYSTEM_CHANGE) {
            Object wsObj = context.metadata().get("workspace.root");
            if (wsObj instanceof Path workspace) {
                List<String> paths = WorkspaceBoundaryValidator.extractPaths(args);
                for (String p : paths) {
                    Optional<String> violation = WorkspaceBoundaryValidator.validate(p, workspace);
                    if (violation.isPresent()) {
                        return Mono.just(GuardrailDecision.deny(violation.get(), name()));
                    }
                }
            }
        }

        // SYSTEM_CHANGE tools: Tier 1 catastrophic command check ONLY
        if (sideEffect == ToolSideEffect.SYSTEM_CHANGE) {
            String cmd = extractCommand(args);
            if (cmd != null) {
                Optional<String> catastrophic = commandPolicy.checkCatastrophic(cmd);
                if (catastrophic.isPresent()) {
                    return Mono.just(GuardrailDecision.deny(catastrophic.get(), name()));
                }
            }
        }

        return Mono.just(GuardrailDecision.allow(name()));
    }

    /**
     * Convention-based command extraction: checks "command" key first, then "subcommand" prefixed
     * with "git ".
     */
    private String extractCommand(Map<String, Object> args) {
        Object cmd = args.get("command");
        if (cmd instanceof String s && !s.isBlank()) {
            return s;
        }
        Object sub = args.get("subcommand");
        if (sub instanceof String s && !s.isBlank()) {
            return "git " + s;
        }
        return null;
    }

    /** Extracts the tool arguments from the guardrail payload. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractArgs(GuardrailContext context) {
        GuardrailPayload payload = context.payload();
        if (payload instanceof GuardrailPayload.ToolInput toolInput) {
            return toolInput.args() != null ? toolInput.args() : Collections.emptyMap();
        }
        return Collections.emptyMap();
    }
}
