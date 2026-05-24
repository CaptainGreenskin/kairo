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

import io.kairo.api.plugin.PluginComponent.HookComponent.HookAction;
import io.kairo.plugin.hook.HookActionHandler;
import io.kairo.plugin.hook.HookExecutor;
import io.kairo.plugin.variable.PluginVariableResolver;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Handles {@code "type": "mcp_tool"} hook actions by routing through an {@link McpToolDispatcher}
 * provided by the host application.
 *
 * <p>Action shape (Claude Code compatible):
 *
 * <pre>{@code
 * {
 *   "type": "mcp_tool",
 *   "server": "my_server",
 *   "tool": "security_scan",
 *   "input": { "file_path": "${tool_input.file_path}" },
 *   "timeout": 600
 * }
 * }</pre>
 *
 * <p>The {@code input} object is shallow-merged onto the event payload (event payload first, then
 * action {@code input} overrides) — this matches Claude Code's behaviour where the action input
 * augments the implicit event context. String values inside {@code input} pass through {@link
 * PluginVariableResolver} (so {@code ${KAIRO_PLUGIN_ROOT}} etc. work).
 */
public final class McpToolHookActionHandler implements HookActionHandler {

    private static final long DEFAULT_TIMEOUT = 600;

    private static final Logger log = LoggerFactory.getLogger(McpToolHookActionHandler.class);

    private final McpToolDispatcher dispatcher;

    public McpToolHookActionHandler(McpToolDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String type() {
        return "mcp_tool";
    }

    @Override
    public Mono<HookExecutor.HookResult> execute(
            HookAction action, Map<String, Object> payload, PluginVariableResolver resolver) {
        String server = stringOrNull(action.config(), "server");
        String tool = stringOrNull(action.config(), "tool");
        if (server == null || server.isBlank() || tool == null || tool.isBlank()) {
            return Mono.just(
                    HookExecutor.HookResult.error(
                            "mcp_tool action requires 'server' and 'tool' fields"));
        }

        long timeout = numberOrDefault(action.config(), "timeout", DEFAULT_TIMEOUT);
        Map<String, Object> mergedInput = mergeInput(payload, action, resolver);

        return dispatcher
                .dispatch(server, tool, mergedInput)
                .timeout(Duration.ofSeconds(timeout))
                .map(
                        toolResult -> {
                            String text =
                                    toolResult.content() == null
                                            ? ""
                                            : toolResult.content().toString();
                            int exit = toolResult.isError() ? 1 : 0;
                            return new HookExecutor.HookResult(exit, text, "", Map.of());
                        })
                .onErrorResume(
                        err -> {
                            log.warn(
                                    "mcp_tool hook '{}.{}' failed: {}",
                                    server,
                                    tool,
                                    err.getMessage() == null
                                            ? err.getClass().getSimpleName()
                                            : err.getMessage());
                            return Mono.just(HookExecutor.HookResult.error(err.getMessage()));
                        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeInput(
            Map<String, Object> payload, HookAction action, PluginVariableResolver resolver) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (payload != null) merged.putAll(payload);
        Object inputRaw = action.config().get("input");
        if (inputRaw instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                merged.put(String.valueOf(e.getKey()), resolveValue(e.getValue(), resolver));
            }
        }
        return merged;
    }

    private Object resolveValue(Object value, PluginVariableResolver resolver) {
        if (value instanceof String s && resolver != null) {
            return resolver.resolve(s);
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> sub = new HashMap<>();
            m.forEach((k, v) -> sub.put(String.valueOf(k), resolveValue(v, resolver)));
            return sub;
        }
        return value;
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
