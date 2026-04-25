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
 * Workspace}, plus user-injected runtime dependencies. During crash recovery, the {@code
 * idempotencyKey} is populated to enable tools to detect duplicate invocations.
 *
 * <p>The {@code tenant} component is part of the v1.1 passive multi-tenant contract: tools READ
 * tenant attribution to scope their actions, but the framework — not the tool — is responsible for
 * binding it. The {@code workspace} component lets file tools resolve relative paths against an
 * explicit working directory rather than the ambient JVM cwd. Constructors that omit either
 * component default to the corresponding sentinel ({@link TenantContext#SINGLE} / {@link
 * Workspace#cwd()}).
 *
 * @param agentId the ID of the agent invoking the tool
 * @param sessionId the current session ID
 * @param dependencies user-injected runtime dependencies (e.g., database connections, API clients)
 * @param idempotencyKey optional key for at-least-once idempotency during crash recovery (null in
 *     normal execution)
 * @param tenant the active tenant; never null (defaults to {@link TenantContext#SINGLE})
 * @param workspace the active workspace; never null (defaults to {@link Workspace#cwd()})
 */
@Stable(value = "Tool runtime context record; tenant + workspace added in v1.1", since = "1.0.0")
public record ToolContext(
        String agentId,
        String sessionId,
        Map<String, Object> dependencies,
        @Nullable String idempotencyKey,
        TenantContext tenant,
        Workspace workspace) {

    /**
     * Compact constructor — defensively copies dependencies, defaults tenant to {@link
     * TenantContext#SINGLE} and workspace to {@link Workspace#cwd()}.
     */
    public ToolContext {
        dependencies = dependencies == null ? Map.of() : Map.copyOf(dependencies);
        tenant = tenant == null ? TenantContext.SINGLE : tenant;
        workspace = workspace == null ? Workspace.cwd() : workspace;
    }

    /**
     * Backward-compatible constructor without workspace. Defaults workspace to {@link
     * Workspace#cwd()}.
     */
    public ToolContext(
            String agentId,
            String sessionId,
            Map<String, Object> dependencies,
            @Nullable String idempotencyKey,
            TenantContext tenant) {
        this(agentId, sessionId, dependencies, idempotencyKey, tenant, Workspace.cwd());
    }

    /**
     * Backward-compatible constructor without tenant or workspace. Defaults tenant to {@link
     * TenantContext#SINGLE} and workspace to {@link Workspace#cwd()}.
     */
    public ToolContext(
            String agentId,
            String sessionId,
            Map<String, Object> dependencies,
            @Nullable String idempotencyKey) {
        this(
                agentId,
                sessionId,
                dependencies,
                idempotencyKey,
                TenantContext.SINGLE,
                Workspace.cwd());
    }

    /**
     * Backward-compatible constructor without idempotency key, tenant, or workspace. Defaults all
     * three to their sentinels.
     */
    public ToolContext(String agentId, String sessionId, Map<String, Object> dependencies) {
        this(agentId, sessionId, dependencies, null, TenantContext.SINGLE, Workspace.cwd());
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
     *
     * <p>This is a thin convenience wrapper over {@link #dependencies()}; user-supplied business
     * dependencies (DB connections, API clients) typically use shorter, role-based keys instead and
     * should be retrieved directly via {@code dependencies().get("...")}.
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
