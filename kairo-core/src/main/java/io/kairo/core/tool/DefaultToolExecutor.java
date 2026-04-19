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

import io.kairo.api.exception.PlanModeViolationException;
import io.kairo.api.tool.*;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default implementation of {@link ToolExecutor} that dispatches tool calls to {@link ToolHandler}
 * instances registered in a {@link DefaultToolRegistry}.
 *
 * <p>Handles permission checks via {@link PermissionGuard}, timeout management, and parallel
 * execution via Project Reactor.
 */
public class DefaultToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    private static final int DEFAULT_CIRCUIT_BREAKER_THRESHOLD = 3;

    private final DefaultToolRegistry registry;
    private final PermissionGuard permissionGuard;
    private final Tracer tracer;
    private final GracefulShutdownManager shutdownManager;
    private final int circuitBreakerThreshold;
    private final ConcurrentHashMap<String, AtomicInteger> consecutiveFailures =
            new ConcurrentHashMap<>();
    private UserApprovalHandler approvalHandler;
    private volatile boolean planMode = false;
    private final Map<String, ToolPermission> toolPermissions = new ConcurrentHashMap<>();
    private volatile Set<String> activeToolConstraints = null; // null = no restriction

    /**
     * Create a new executor with the given registry and permission guard.
     *
     * @param registry the tool registry
     * @param permissionGuard the permission guard
     */
    public DefaultToolExecutor(DefaultToolRegistry registry, PermissionGuard permissionGuard) {
        this(registry, permissionGuard, null, null);
    }

    /**
     * Create a new executor with the given registry, permission guard, and tracer.
     *
     * @param registry the tool registry
     * @param permissionGuard the permission guard
     * @param tracer the tracer (null defaults to NoopTracer)
     */
    public DefaultToolExecutor(
            DefaultToolRegistry registry, PermissionGuard permissionGuard, Tracer tracer) {
        this(registry, permissionGuard, tracer, null);
    }

    /**
     * Create a new executor with the given registry, permission guard, tracer, and shutdown
     * manager.
     *
     * @param registry the tool registry
     * @param permissionGuard the permission guard
     * @param tracer the tracer (null defaults to NoopTracer)
     * @param shutdownManager the shutdown manager (null creates a new instance)
     */
    public DefaultToolExecutor(
            DefaultToolRegistry registry,
            PermissionGuard permissionGuard,
            Tracer tracer,
            GracefulShutdownManager shutdownManager) {
        this(registry, permissionGuard, tracer, shutdownManager, DEFAULT_CIRCUIT_BREAKER_THRESHOLD);
    }

    /**
     * Create a new executor with all options including circuit breaker threshold.
     *
     * @param registry the tool registry
     * @param permissionGuard the permission guard
     * @param tracer the tracer (null defaults to NoopTracer)
     * @param shutdownManager the shutdown manager (null creates a new instance)
     * @param circuitBreakerThreshold consecutive failures before a tool is circuit-broken
     */
    public DefaultToolExecutor(
            DefaultToolRegistry registry,
            PermissionGuard permissionGuard,
            Tracer tracer,
            GracefulShutdownManager shutdownManager,
            int circuitBreakerThreshold) {
        this.registry = registry;
        this.permissionGuard = permissionGuard;
        this.tracer = tracer != null ? tracer : new io.kairo.api.tracing.NoopTracer();
        this.shutdownManager =
                shutdownManager != null ? shutdownManager : new GracefulShutdownManager();
        this.circuitBreakerThreshold =
                circuitBreakerThreshold > 0
                        ? circuitBreakerThreshold
                        : DEFAULT_CIRCUIT_BREAKER_THRESHOLD;
    }

    /**
     * Set the approval handler for human-in-the-loop confirmation.
     *
     * @param handler the approval handler, or null to disable approval flow
     */
    @Override
    public void setApprovalHandler(UserApprovalHandler handler) {
        this.approvalHandler = handler;
    }

    /**
     * Set the allowed tools whitelist for the currently active skill. When set, only tools in this
     * set can be executed.
     *
     * @param allowed set of tool names that are allowed, or null to clear
     */
    @Override
    public void setAllowedTools(Set<String> allowed) {
        this.activeToolConstraints = allowed;
    }

    /** Clear the active tool constraints, allowing all tools to execute. */
    @Override
    public void clearAllowedTools() {
        this.activeToolConstraints = null;
    }

    /**
     * Set plan mode on or off.
     *
     * <p>When plan mode is active, only read-only tools are allowed. Write and system-change tools
     * will throw {@link PlanModeViolationException}.
     *
     * @param planMode true to enter plan mode, false to exit
     */
    public void setPlanMode(boolean planMode) {
        this.planMode = planMode;
    }

    /**
     * Check if plan mode is currently active.
     *
     * @return true if in plan mode
     */
    public boolean isPlanMode() {
        return planMode;
    }

    /**
     * Set a tool-specific permission override.
     *
     * @param toolName the tool name
     * @param permission the permission level
     */
    public void setToolPermission(String toolName, ToolPermission permission) {
        toolPermissions.put(toolName, permission);
    }

    /**
     * Set a default permission for all tools with the given side-effect classification.
     *
     * @param sideEffect the side-effect category
     * @param permission the permission level
     */
    public void setDefaultPermission(ToolSideEffect sideEffect, ToolPermission permission) {
        toolPermissions.put("__category__" + sideEffect.name(), permission);
    }

    @Override
    public Mono<ToolResult> execute(String toolName, Map<String, Object> input) {
        // Look up tool definition to get per-tool timeout
        ToolDefinition definition = registry.get(toolName).orElse(null);
        Duration timeout =
                (definition != null && definition.timeout() != null)
                        ? definition.timeout()
                        : DEFAULT_TIMEOUT;
        return execute(toolName, input, timeout);
    }

    @Override
    public Mono<ToolResult> execute(String toolName, Map<String, Object> input, Duration timeout) {
        Span toolSpan = tracer.startToolSpan(null, toolName, input);
        return executeInternal(toolName, input, timeout)
                .doOnSuccess(
                        result -> {
                            toolSpan.setStatus(
                                    !result.isError(), result.isError() ? result.content() : "OK");
                            toolSpan.end();
                        })
                .doOnError(
                        e -> {
                            toolSpan.setStatus(false, e.getMessage());
                            toolSpan.end();
                        });
    }

    /**
     * Check plan mode restrictions before executing a tool.
     *
     * @param toolName the tool name
     * @throws PlanModeViolationException if the tool is blocked in plan mode
     */
    private void checkPlanModeRestriction(String toolName) {
        if (planMode) {
            var sideEffect = resolveSideEffect(toolName);
            if (sideEffect == ToolSideEffect.WRITE || sideEffect == ToolSideEffect.SYSTEM_CHANGE) {
                throw new PlanModeViolationException(
                        "Tool '"
                                + toolName
                                + "' ("
                                + sideEffect
                                + ") is blocked in Plan Mode. "
                                + "Only read-only tools are available. Exit plan mode first.",
                        toolName);
            }
        }
    }

    private Mono<ToolResult> executeInternal(
            String toolName, Map<String, Object> input, Duration timeout) {
        return Mono.defer(
                () -> {
                    // 0. Circuit breaker check
                    AtomicInteger failures = consecutiveFailures.get(toolName);
                    if (failures != null && failures.get() >= circuitBreakerThreshold) {
                        return Mono.just(
                                errorResult(
                                        toolName,
                                        "Tool '"
                                                + toolName
                                                + "' is circuit-broken after "
                                                + failures.get()
                                                + " consecutive failures. Reset by"
                                                + " successful execution or agent"
                                                + " restart."));
                    }

                    // 0a. Active skill tool constraints check
                    if (activeToolConstraints != null
                            && !activeToolConstraints.contains(toolName)
                            && !"skill_load".equals(toolName)
                            && !"skill_list".equals(toolName)) {
                        return Mono.just(
                                errorResult(
                                        toolName,
                                        "Tool '"
                                                + toolName
                                                + "' is not allowed by the active skill. Allowed tools: "
                                                + activeToolConstraints));
                    }

                    // 0b. Plan mode check — must be before any execution
                    try {
                        checkPlanModeRestriction(toolName);
                    } catch (PlanModeViolationException e) {
                        return Mono.just(errorResult(toolName, e.getMessage()));
                    }

                    // 1. Look up tool definition
                    ToolDefinition definition = registry.get(toolName).orElse(null);
                    if (definition == null) {
                        return Mono.just(errorResult(toolName, "Unknown tool: " + toolName));
                    }

                    // 2. Get tool handler instance
                    Object instance = registry.getToolInstance(toolName);
                    if (!(instance instanceof ToolHandler handler)) {
                        return Mono.just(
                                errorResult(
                                        toolName,
                                        "Tool '" + toolName + "' has no executable handler"));
                    }

                    // 3. Check permissions
                    return permissionGuard
                            .checkPermissionDetail(toolName, input)
                            .flatMap(
                                    decision -> {
                                        if (!decision.allowed()) {
                                            String msg =
                                                    "Permission denied: "
                                                            + decision.reason()
                                                            + (decision.policyId() != null
                                                                    ? " [policy: "
                                                                            + decision.policyId()
                                                                            + "]"
                                                                    : "");
                                            return Mono.just(errorResult(toolName, msg));
                                        }
                                        // 4. Execute the tool with shutdown guard
                                        Mono<ToolResult> execution =
                                                Mono.fromCallable(() -> handler.execute(input))
                                                        .subscribeOn(Schedulers.boundedElastic())
                                                        .timeout(timeout)
                                                        .onErrorResume(
                                                                e -> {
                                                                    if (e
                                                                            instanceof
                                                                            java.util.concurrent
                                                                                    .TimeoutException) {
                                                                        return Mono.just(
                                                                                errorResult(
                                                                                        toolName,
                                                                                        "Tool execution timed out after "
                                                                                                + timeout
                                                                                                        .getSeconds()
                                                                                                + "s"));
                                                                    }
                                                                    log.error(
                                                                            "Tool '{}' execution"
                                                                                    + " failed",
                                                                            toolName,
                                                                            e);
                                                                    return Mono.just(
                                                                            errorResult(
                                                                                    toolName,
                                                                                    "Error: "
                                                                                            + e
                                                                                                    .getMessage()));
                                                                });

                                        // Race against shutdown signal
                                        // (shutdown guard pattern)
                                        Mono<ToolResult> shutdownGuard =
                                                shutdownManager
                                                        .getShutdownSignal()
                                                        .then(
                                                                Mono.just(
                                                                        errorResult(
                                                                                toolName,
                                                                                "Tool aborted"
                                                                                        + " due to"
                                                                                        + " system"
                                                                                        + " shutdown")));
                                        return Mono.firstWithSignal(execution, shutdownGuard)
                                                .doOnNext(this::trackCircuitBreaker)
                                                .map(this::applySanitizer);
                                    });
                });
    }

    @Override
    public Flux<ToolResult> executeParallel(List<ToolInvocation> invocations) {
        return executePartitioned(invocations);
    }

    /**
     * Execute tool invocations with read/write partitioning.
     *
     * <p>READ_ONLY tools are executed in parallel (safe, no side effects), while WRITE and
     * SYSTEM_CHANGE tools are executed serially in their original order. Results are returned in
     * the original invocation order.
     *
     * @param invocations the tool invocations to execute
     * @return a Flux emitting results in original invocation order
     */
    public Flux<ToolResult> executePartitioned(List<ToolInvocation> invocations) {
        return Mono.defer(
                        () -> {
                            // 1. Partition invocations by side effect
                            var readInvocations = new ArrayList<ToolInvocation>();
                            var writeInvocations = new ArrayList<ToolInvocation>();
                            for (var inv : invocations) {
                                var sideEffect = resolveSideEffect(inv.toolName());
                                if (sideEffect == ToolSideEffect.READ_ONLY) {
                                    readInvocations.add(inv);
                                } else {
                                    writeInvocations.add(inv);
                                }
                            }

                            // 2. Execute reads in parallel (safe, no side effects)
                            var results = new LinkedHashMap<ToolInvocation, ToolResult>();
                            Mono<Void> readPhase = Mono.empty();
                            if (!readInvocations.isEmpty()) {
                                readPhase =
                                        Flux.fromIterable(readInvocations)
                                                .flatMap(
                                                        inv ->
                                                                executeWithApproval(inv)
                                                                        .doOnNext(
                                                                                result -> {
                                                                                    synchronized (
                                                                                            results) {
                                                                                        results.put(
                                                                                                inv,
                                                                                                result);
                                                                                    }
                                                                                }))
                                                .then();
                            }

                            // 3. Execute writes serially (has side effects, order matters)
                            Mono<Void> writePhase =
                                    Flux.fromIterable(writeInvocations)
                                            .concatMap(
                                                    inv ->
                                                            executeWithApproval(inv)
                                                                    .doOnNext(
                                                                            result -> {
                                                                                synchronized (
                                                                                        results) {
                                                                                    results.put(
                                                                                            inv,
                                                                                            result);
                                                                                }
                                                                            }))
                                            .then();

                            // 4. Return results in original invocation order
                            return readPhase.then(writePhase).thenReturn(results);
                        })
                .flatMapMany(
                        results ->
                                Flux.fromIterable(invocations)
                                        .map(
                                                inv ->
                                                        results.getOrDefault(
                                                                inv,
                                                                errorResult(
                                                                        inv.toolName(),
                                                                        "Tool result missing"))));
    }

    /**
     * Resolve the side-effect classification for a tool by name.
     *
     * <p>Unknown tools default to {@link ToolSideEffect#SYSTEM_CHANGE} (safest assumption).
     *
     * @param toolName the tool name
     * @return the side-effect classification
     */
    @Override
    public ToolSideEffect resolveSideEffect(String toolName) {
        var def = registry.get(toolName);
        if (def.isEmpty()) {
            log.warn(
                    "Tool '{}' has no registered definition, defaulting to SYSTEM_CHANGE",
                    toolName);
            return ToolSideEffect.SYSTEM_CHANGE;
        }
        return def.get().sideEffect();
    }

    /**
     * Resolve the permission level for a tool.
     *
     * <p>Resolution order: tool-specific override → category-level (by SideEffect) → default.
     * Defaults: READ_ONLY and WRITE → ALLOWED, SYSTEM_CHANGE → ASK.
     *
     * @param toolName the tool name
     * @param sideEffect the side-effect classification
     * @return the resolved permission
     */
    private ToolPermission resolvePermission(String toolName, ToolSideEffect sideEffect) {
        // 1. Tool-specific override
        var toolPerm = toolPermissions.get(toolName);
        if (toolPerm != null) return toolPerm;

        // 2. Category-level (by SideEffect)
        var categoryPerm = toolPermissions.get("__category__" + sideEffect.name());
        if (categoryPerm != null) return categoryPerm;

        // 3. Default: READ_ONLY → ALLOWED, WRITE → ALLOWED, SYSTEM_CHANGE → ASK
        return sideEffect == ToolSideEffect.SYSTEM_CHANGE
                ? ToolPermission.ASK
                : ToolPermission.ALLOWED;
    }

    /**
     * Execute a tool invocation with approval check.
     *
     * <p>Checks the resolved permission for the tool and either executes directly, denies, or
     * requests user approval via the configured {@link UserApprovalHandler}.
     *
     * @param invocation the tool invocation
     * @return a Mono emitting the tool result
     */
    private Mono<ToolResult> executeWithApproval(ToolInvocation invocation) {
        var sideEffect = resolveSideEffect(invocation.toolName());
        var permission = resolvePermission(invocation.toolName(), sideEffect);

        return switch (permission) {
            case ALLOWED -> execute(invocation.toolName(), invocation.input());
            case DENIED ->
                    Mono.just(
                            errorResult(
                                    invocation.toolName(),
                                    "Tool '"
                                            + invocation.toolName()
                                            + "' is denied by permission policy"));
            case ASK -> {
                if (approvalHandler == null) {
                    // No handler configured → deny by default for safety
                    yield Mono.just(
                            errorResult(
                                    invocation.toolName(),
                                    "Tool '"
                                            + invocation.toolName()
                                            + "' requires approval but no handler configured"));
                }
                var request =
                        new ToolCallRequest(invocation.toolName(), invocation.input(), sideEffect);
                yield approvalHandler
                        .requestApproval(request)
                        .flatMap(
                                result -> {
                                    if (result.approved()) {
                                        return execute(invocation.toolName(), invocation.input());
                                    }
                                    return Mono.just(
                                            errorResult(
                                                    invocation.toolName(),
                                                    "Tool '"
                                                            + invocation.toolName()
                                                            + "' denied by user: "
                                                            + result.reason()));
                                });
            }
        };
    }

    /**
     * Execute a single tool invocation with approval flow.
     *
     * <p>This is the public entry point for streaming executors that need to dispatch individual
     * tool calls through the same permission and approval pipeline as {@link
     * #executePartitioned(List)}.
     *
     * @param invocation the tool invocation to execute
     * @return a Mono emitting the tool result
     */
    @Override
    public Mono<ToolResult> executeSingle(ToolInvocation invocation) {
        return executeWithApproval(invocation);
    }

    @Override
    public void registerToolInstance(String toolName, Object instance) {
        registry.registerInstance(toolName, instance);
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    /**
     * Track consecutive failures for circuit breaker logic.
     *
     * @param result the tool result to evaluate
     */
    private void trackCircuitBreaker(ToolResult result) {
        if (result.isError()) {
            consecutiveFailures
                    .computeIfAbsent(result.toolUseId(), k -> new AtomicInteger())
                    .incrementAndGet();
        } else {
            consecutiveFailures.remove(result.toolUseId());
        }
    }

    /** Reset circuit breaker state for all tools. */
    @Override
    public void resetCircuitBreaker() {
        consecutiveFailures.clear();
    }

    /** Reset circuit breaker state for a specific tool. */
    @Override
    public void resetCircuitBreaker(String toolName) {
        consecutiveFailures.remove(toolName);
    }

    /**
     * Apply the {@link ToolOutputSanitizer} to a tool result and attach any warnings as metadata.
     *
     * <p>If the scan produces warnings, a new {@link ToolResult} is returned with an {@code
     * "injection_warning"} metadata entry containing the warning list. The original result is
     * returned unchanged when no warnings are found.
     *
     * @param result the original tool result
     * @return the result, possibly enriched with warning metadata
     */
    private ToolResult applySanitizer(ToolResult result) {
        if (result.isError()) {
            return result;
        }
        var scanResult = ToolOutputSanitizer.scan(result.content());
        if (!scanResult.hasWarnings()) {
            return result;
        }
        log.warn(
                "Tool '{}' output triggered {} injection warning(s): {}",
                result.toolUseId(),
                scanResult.warnings().size(),
                scanResult.warnings());
        var enrichedMetadata = new HashMap<>(result.metadata());
        enrichedMetadata.put("injection_warning", scanResult.warnings());
        return new ToolResult(
                result.toolUseId(), result.content(), result.isError(), enrichedMetadata);
    }

    /** Create an error {@link ToolResult}. */
    private ToolResult errorResult(String toolName, String message) {
        return new ToolResult(toolName, message, true, Map.of());
    }
}
