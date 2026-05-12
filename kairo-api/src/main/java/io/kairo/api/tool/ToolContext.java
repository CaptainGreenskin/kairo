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
package io.kairo.api.tool;

import io.kairo.api.Stable;
import io.kairo.api.tenant.TenantContext;
import io.kairo.api.workspace.Workspace;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Runtime context provided to tool executions.
 *
 * <p>Contains agent and session identifiers, the active {@link TenantContext}, the active {@link
 * Workspace}, an {@link OutputBudgetConfig} for output size management, plus user-injected runtime
 * dependencies. During crash recovery, the {@code idempotencyKey} is populated to enable tools to
 * detect duplicate invocations.
 *
 * <p>The {@code budget} component (v1.2) tells tools and the executor how much output is
 * permissible before truncation kicks in. The {@code tenant} component is part of the v1.1 passive
 * multi-tenant contract: tools READ tenant attribution to scope their actions, but the framework —
 * not the tool — is responsible for binding it. The {@code workspace} component lets file tools
 * resolve relative paths against an explicit working directory rather than the ambient JVM cwd.
 *
 * @param agentId the ID of the agent invoking the tool
 * @param sessionId the current session ID
 * @param budget output budget configuration for this execution context
 * @param workspace the active workspace; never null (defaults to {@link Workspace#cwd()})
 * @param tenant the active tenant; never null (defaults to {@link TenantContext#SINGLE})
 * @param idempotencyKey optional key for at-least-once idempotency during crash recovery
 * @param dependencies user-injected runtime dependencies (e.g., database connections, API clients)
 * @since 1.2.0
 */
@Stable(value = "Tool runtime context record; budget added in v1.2", since = "1.0.0")
public record ToolContext(
        String agentId,
        String sessionId,
        OutputBudgetConfig budget,
        Workspace workspace,
        TenantContext tenant,
        Optional<String> idempotencyKey,
        Map<String, Object> dependencies) {

    /**
     * Compact constructor — applies safe defaults for null fields and defensively copies
     * dependencies.
     */
    public ToolContext {
        budget = budget == null ? OutputBudgetConfig.DEFAULT : budget;
        workspace = workspace == null ? Workspace.cwd() : workspace;
        tenant = tenant == null ? TenantContext.SINGLE : tenant;
        idempotencyKey = idempotencyKey == null ? Optional.empty() : idempotencyKey;
        dependencies = dependencies == null ? Map.of() : Map.copyOf(dependencies);
    }

    /**
     * Backward-compatible constructor: (agentId, sessionId, dependencies). Defaults budget,
     * workspace, tenant, and idempotencyKey to their sentinels.
     */
    public ToolContext(String agentId, String sessionId, Map<String, Object> dependencies) {
        this(
                agentId,
                sessionId,
                OutputBudgetConfig.DEFAULT,
                Workspace.cwd(),
                TenantContext.SINGLE,
                Optional.empty(),
                dependencies);
    }

    /**
     * Backward-compatible constructor with nullable idempotency key: (agentId, sessionId,
     * dependencies, idempotencyKey).
     */
    public ToolContext(
            String agentId,
            String sessionId,
            Map<String, Object> dependencies,
            @Nullable String idempotencyKey) {
        this(
                agentId,
                sessionId,
                OutputBudgetConfig.DEFAULT,
                Workspace.cwd(),
                TenantContext.SINGLE,
                Optional.ofNullable(idempotencyKey),
                dependencies);
    }

    /**
     * Backward-compatible constructor with nullable idempotency key and tenant: (agentId,
     * sessionId, dependencies, idempotencyKey, tenant).
     */
    public ToolContext(
            String agentId,
            String sessionId,
            Map<String, Object> dependencies,
            @Nullable String idempotencyKey,
            TenantContext tenant) {
        this(
                agentId,
                sessionId,
                OutputBudgetConfig.DEFAULT,
                Workspace.cwd(),
                tenant,
                Optional.ofNullable(idempotencyKey),
                dependencies);
    }

    /**
     * Backward-compatible constructor with all v1.1 fields: (agentId, sessionId, dependencies,
     * idempotencyKey, tenant, workspace).
     */
    public ToolContext(
            String agentId,
            String sessionId,
            Map<String, Object> dependencies,
            @Nullable String idempotencyKey,
            TenantContext tenant,
            Workspace workspace) {
        this(
                agentId,
                sessionId,
                OutputBudgetConfig.DEFAULT,
                workspace,
                tenant,
                Optional.ofNullable(idempotencyKey),
                dependencies);
    }

    /**
     * Looks up a framework-bound dependency by its declared type, returning {@link
     * Optional#empty()} when none is bound.
     *
     * <p>Convention: framework wiring stores SPI components keyed by their fully-qualified
     * interface name (e.g. {@code io.kairo.api.sandbox.ExecutionSandbox}). Tools use this helper to
     * resolve optional SPI overrides — for example {@code BashTool} reads the active {@link
     * io.kairo.api.sandbox.ExecutionSandbox} here and falls back to the bundled local
     * implementation when none is bound.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getBean(Class<T> type) {
        if (type == null) {
            return Optional.empty();
        }
        Object value = dependencies.get(type.getName());
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }
}
