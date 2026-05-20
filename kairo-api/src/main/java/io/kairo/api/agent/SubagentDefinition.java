/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.agent;

import io.kairo.api.Experimental;
import java.util.List;

/**
 * Declarative description of a subagent — the LLM persona that a parent agent can delegate work to.
 * Mirrors the Claude Code {@code agents/*.md} schema (frontmatter for metadata, body as the system
 * prompt).
 *
 * <p>Registration happens via {@link SubagentRegistry}. Actual scheduling and execution is the job
 * of an {@code AgentRuntime} implementation (e.g. {@code kairo-multi-agent}); this SPI is only the
 * catalog of available personas.
 *
 * @param name unique subagent name (kebab-case recommended)
 * @param description short summary used by the parent agent to decide whether to delegate
 * @param systemPrompt the full system prompt body (markdown)
 * @param tools optional whitelist of tool names the subagent may use; empty means inherit parent
 * @param model optional model alias or full model name to pin; null means inherit parent
 * @param namespace plugin namespace (or null for unnamespaced); used by {@code <namespace>:<name>}
 *     invocation syntax
 * @since 1.2
 */
@Experimental("Subagent SPI — contract may change in v1.x")
public record SubagentDefinition(
        String name,
        String description,
        String systemPrompt,
        List<String> tools,
        String model,
        String namespace) {

    public SubagentDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Subagent name must not be blank");
        }
        if (systemPrompt == null) {
            throw new IllegalArgumentException("systemPrompt must not be null");
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    /**
     * Returns the qualified name {@code <namespace>:<name>} when namespaced, otherwise the bare
     * {@link #name()}.
     */
    public String qualifiedName() {
        return namespace == null || namespace.isBlank() ? name : namespace + ":" + name;
    }
}
