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
package io.kairo.multiagent.orchestration.tool;

import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorates a {@link ToolExecutor} to enforce role-based tool restrictions.
 *
 * <p>If the role's allowed-tools set is non-empty, only those tools may be invoked; any other tool
 * call receives an immediate {@link ToolResult} with {@link io.kairo.api.tool.ToolOutcome#ERROR
 * ERROR} outcome and a descriptive message. An empty allowed-tools set means all tools are
 * permitted (no restriction).
 *
 * <p>This decorator delegates all permitted calls to the wrapped {@code delegate} executor
 * unchanged.
 *
 * @since v0.10
 */
public class RoleScopedToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final Set<String> allowedTools;
    private final String roleId;

    /**
     * @param delegate the underlying executor to delegate permitted calls to; must not be null
     * @param allowedTools the tool allowlist; empty list means all tools permitted
     * @param roleId the role identifier, used in error messages; must not be null or blank
     */
    public RoleScopedToolExecutor(ToolExecutor delegate, List<String> allowedTools, String roleId) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(allowedTools, "allowedTools must not be null");
        if (roleId == null || roleId.isBlank()) {
            throw new IllegalArgumentException("roleId must not be null or blank");
        }
        this.delegate = delegate;
        this.allowedTools = allowedTools.isEmpty() ? Set.of() : Set.copyOf(allowedTools);
        this.roleId = roleId;
    }

    @Override
    public Mono<ToolResult> execute(String toolName, Map<String, Object> input) {
        if (isBlocked(toolName)) {
            return Mono.just(blockedResult(toolName, ""));
        }
        return delegate.execute(toolName, input);
    }

    @Override
    public Mono<ToolResult> execute(String toolName, Map<String, Object> input, Duration timeout) {
        if (isBlocked(toolName)) {
            return Mono.just(blockedResult(toolName, ""));
        }
        return delegate.execute(toolName, input, timeout);
    }

    @Override
    public Flux<ToolResult> executeParallel(List<ToolInvocation> invocations) {
        // Split into allowed and blocked invocations
        List<ToolInvocation> allowed =
                invocations.stream().filter(inv -> !isBlocked(inv.toolName())).toList();

        List<ToolResult> blocked =
                invocations.stream()
                        .filter(inv -> isBlocked(inv.toolName()))
                        .map(
                                inv ->
                                        blockedResult(
                                                inv.toolName(),
                                                inv.toolCallId() != null ? inv.toolCallId() : ""))
                        .toList();

        Flux<ToolResult> blockedFlux = Flux.fromIterable(blocked);
        if (allowed.isEmpty()) {
            return blockedFlux;
        }
        return delegate.executeParallel(allowed).concatWith(blockedFlux);
    }

    @Override
    public Mono<ToolResult> executeSingle(ToolInvocation invocation) {
        if (isBlocked(invocation.toolName())) {
            return Mono.just(
                    blockedResult(
                            invocation.toolName(),
                            invocation.toolCallId() != null ? invocation.toolCallId() : ""));
        }
        return delegate.executeSingle(invocation);
    }

    // ── Delegation of non-execution methods ──────────────────────────────────

    @Override
    public void setAllowedTools(Set<String> tools) {
        delegate.setAllowedTools(tools);
    }

    @Override
    public void clearAllowedTools() {
        delegate.clearAllowedTools();
    }

    @Override
    public void registerToolInstance(String toolName, Object instance) {
        delegate.registerToolInstance(toolName, instance);
    }

    @Override
    public void setToolMetadata(String toolName, Map<String, Object> metadata) {
        delegate.setToolMetadata(toolName, metadata);
    }

    @Override
    public boolean supportsStreaming() {
        return delegate.supportsStreaming();
    }

    @Override
    public io.kairo.api.tool.ToolSideEffect resolveSideEffect(String toolName) {
        return delegate.resolveSideEffect(toolName);
    }

    @Override
    public void setApprovalHandler(io.kairo.api.tool.UserApprovalHandler approvalHandler) {
        delegate.setApprovalHandler(approvalHandler);
    }

    @Override
    public void resetCircuitBreaker() {
        delegate.resetCircuitBreaker();
    }

    @Override
    public void resetCircuitBreaker(String toolName) {
        delegate.resetCircuitBreaker(toolName);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private boolean isBlocked(String toolName) {
        return !allowedTools.isEmpty() && !allowedTools.contains(toolName);
    }

    private ToolResult blockedResult(String toolName, String toolUseId) {
        String message =
                "Tool '"
                        + toolName
                        + "' is not permitted for role '"
                        + roleId
                        + "'. "
                        + "Allowed tools: "
                        + allowedTools;
        return ToolResult.error(toolUseId, message);
    }
}
