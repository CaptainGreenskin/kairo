/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.hook.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.plugin.PluginComponent.HookComponent.HookAction;
import io.kairo.plugin.hook.HookActionHandler;
import io.kairo.plugin.hook.HookExecutor;
import io.kairo.plugin.variable.PluginVariableResolver;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Handles {@code "type": "agent"} hook actions by delegating to a host-supplied agent runner.
 *
 * <p>The Claude Code semantics for the {@code agent} action type is "spawn a fresh agentic loop
 * (with tool use) to verify or extend the calling agent's work." Kairo doesn't dictate the agent
 * runtime here — the host wires whatever it wants (typically a {@code DefaultReActAgent}).
 *
 * <p>Action shape:
 *
 * <pre>{@code
 * {"type": "agent", "prompt": "Verify $ARGUMENTS", "model": "claude-sonnet-4-6", "timeout": 60}
 * }</pre>
 *
 * <p>{@code $ARGUMENTS} is replaced with the event payload (JSON serialised). The handler hands the
 * resolved prompt + optional model hint to the runner; the runner returns the agent's final
 * response text. JSON-shaped responses are parsed into {@code decision}, matching the Claude Code
 * behaviour where an agent's structured output can drive the calling tool's continuation.
 */
public final class AgentHookActionHandler implements HookActionHandler {

    private static final long DEFAULT_TIMEOUT = 60;

    private static final Logger log = LoggerFactory.getLogger(AgentHookActionHandler.class);

    /** Functional adapter the host supplies. */
    @FunctionalInterface
    public interface AgentRunner {
        /** Runs an agent with the resolved prompt and returns its final response text. */
        Mono<String> run(String prompt, String modelHint);
    }

    private final AgentRunner runner;
    private final ObjectMapper json = new ObjectMapper();

    public AgentHookActionHandler(AgentRunner runner) {
        this.runner = runner;
    }

    @Override
    public String type() {
        return "agent";
    }

    @Override
    public Mono<HookExecutor.HookResult> execute(
            HookAction action, Map<String, Object> payload, PluginVariableResolver resolver) {
        String promptTemplate = stringOrNull(action.config(), "prompt");
        if (promptTemplate == null || promptTemplate.isBlank()) {
            return Mono.just(HookExecutor.HookResult.error("agent action missing 'prompt' field"));
        }

        String resolved = resolver == null ? promptTemplate : resolver.resolve(promptTemplate);
        String prompt = resolved.replace("$ARGUMENTS", serialize(payload));
        String modelHint = stringOrNull(action.config(), "model");
        long timeout = numberOrDefault(action.config(), "timeout", DEFAULT_TIMEOUT);

        return runner.run(prompt, modelHint)
                .timeout(Duration.ofSeconds(timeout))
                .map(text -> new HookExecutor.HookResult(0, text, "", parseDecisionJson(text)))
                .onErrorResume(
                        err -> {
                            log.warn(
                                    "agent hook failed: {}",
                                    err.getMessage() == null
                                            ? err.getClass().getSimpleName()
                                            : err.getMessage());
                            return Mono.just(HookExecutor.HookResult.error(err.getMessage()));
                        });
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return "";
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return payload.toString();
        }
    }

    private Map<String, Object> parseDecisionJson(String body) {
        if (body == null || body.isBlank()) return Map.of();
        try {
            Object parsed = json.readValue(body, Object.class);
            if (parsed instanceof Map<?, ?> m) {
                Map<String, Object> out = new HashMap<>();
                m.forEach((k, v) -> out.put(String.valueOf(k), v));
                return Collections.unmodifiableMap(out);
            }
        } catch (JsonProcessingException ignored) {
            // plain text, that's fine
        }
        return Map.of();
    }

    private String stringOrNull(Map<String, Object> config, String key) {
        Object v = config.get(key);
        return v instanceof String s ? s : null;
    }

    private long numberOrDefault(Map<String, Object> config, String key, long defaultValue) {
        Object v = config.get(key);
        if (v instanceof Number n) return n.longValue();
        return defaultValue;
    }
}
