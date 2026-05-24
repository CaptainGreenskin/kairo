/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.hook;

import io.kairo.api.plugin.PluginComponent.HookComponent.HookAction;
import io.kairo.plugin.variable.PluginVariableResolver;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Pluggable handler for one hook-action type.
 *
 * <p>{@link HookExecutor} ships built-in handlers for {@code command} and {@code http}. The other
 * three Claude-Code-style action types ({@code prompt}, {@code agent}, {@code mcp_tool}) require
 * collaborators (model provider, agent runtime, MCP plugin) that are out of scope for this module —
 * host applications register concrete handlers via {@link HookExecutor#withHandler}.
 *
 * <p>The contract: given a parsed {@link HookAction} and the event payload, run whatever the action
 * describes and return a {@link HookExecutor.HookResult}. Implementations should respect any {@code
 * timeout} field in {@code action.config()} and surface errors via {@link
 * HookExecutor.HookResult#error}.
 */
public interface HookActionHandler {

    /** The action type this handler accepts (e.g. {@code "prompt"}). Unique per registry. */
    String type();

    /**
     * Runs the action. Must not block the caller; use {@code Schedulers.boundedElastic()} for IO.
     */
    Mono<HookExecutor.HookResult> execute(
            HookAction action, Map<String, Object> payload, PluginVariableResolver resolver);
}
