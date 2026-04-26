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
import io.kairo.api.guardrail.*;
import io.kairo.api.tool.*;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link ToolExecutor} that dispatches tool calls to {@link ToolHandler}
 * instances registered in a {@link DefaultToolRegistry}.
 *
 * <p>Orchestrates a pipeline of extracted components:
 *
 * <ol>
 *   <li>{@link ToolCircuitBreakerTracker} — fail-fast when CB is open
 *   <li>{@link ToolPermissionResolver} — plan mode, active-tool constraints, permission resolution
 *   <li>{@link ToolInvocationRunner} — actual handler invocation with timeout + cancellation
 *   <li>{@link ToolResultSanitizer} — output injection scan
 *   <li>{@link ToolApprovalFlow} — user approval workflow for partitioned/single execution
 * </ol>
 *
 * <p>Handles permission checks via {@link PermissionGuard}, timeout management, and parallel
 * execution via Project Reactor.
 */
public class DefaultToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    private static final int DEFAULT_CIRCUIT_BREAKER_THRESHOLD = 3;

    private final DefaultToolRegistry registry;
    private final Tracer tracer;
    private final ToolPermissionResolver permissionResolver;
    private final ToolCircuitBreakerTracker circuitBreakerTracker;
    private final ToolInvocationRunner invocationRunner;
    private final ToolApprovalFlow approvalFlow;
    private final GuardrailChain guardrailChain; // nullable — backward compatible

    /** Reactor Context key used to propagate {@link ToolContext} through the reactive pipeline. */
    public static final Class<ToolContext> CONTEXT_KEY = ToolContext.class;

    /**
     * Create a new executor with the given registry and permission guard.
     *
     * @param registry the tool registry
     * @param permissionGuard the permission guard
     */
    public DefaultToolExecutor(DefaultToolRegistry registry, PermissionGuard permissionGuard) {
        this(registry, permissionGuard, null, null, DEFAULT_CIRCUIT_BREAKER_THRESHOLD, null);
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
        this(registry, permissionGuard, tracer, null, DEFAULT_CIRCUIT_BREAKER_THRESHOLD, null);
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
        this(
                registry,
                permissionGuard,
                tracer,
                shutdownManager,
                DEFAULT_CIRCUIT_BREAKER_THRESHOLD,
                null);
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
        this(registry, permissionGuard, tracer, shutdownManager, circuitBreakerThreshold, null);
    }

    /**
     * Create a new executor with all options including guardrail chain.
     *
     * @param registry the tool registry
     * @param permissionGuard the permission guard
     * @param tracer the tracer (null defaults to NoopTracer)
     * @param shutdownManager the shutdown manager (null creates a new instance)
     * @param circuitBreakerThreshold consecutive failures before a tool is circuit-broken
     * @param guardrailChain the guardrail chain (null skips guardrail evaluation)
     */
    public DefaultToolExecutor(
            DefaultToolRegistry registry,
            PermissionGuard permissionGuard,
            Tracer tracer,
            GracefulShutdownManager shutdownManager,
            int circuitBreakerThreshold,
            GuardrailChain guardrailChain) {
        this.registry = registry;
        this.tracer = tracer != null ? tracer : new io.kairo.api.tracing.NoopTracer();
        GracefulShutdownManager sm =
                shutdownManager != null ? shutdownManager : new GracefulShutdownManager();

        this.permissionResolver = new ToolPermissionResolver(permissionGuard, registry);
        this.circuitBreakerTracker = new ToolCircuitBreakerTracker(circuitBreakerThreshold);
        this.invocationRunner = new ToolInvocationRunner(this.tracer, sm);
        this.approvalFlow = new ToolApprovalFlow(permissionResolver, this);
        this.guardrailChain = guardrailChain;
    }

    // ==================== ToolExecutor interface delegation ====================

    @Override
    public void setApprovalHandler(UserApprovalHandler handler) {
        approvalFlow.setApprovalHandler(handler);
    }

    @Override
    public void setAllowedTools(Set<String> allowed) {
        permissionResolver.setAllowedTools(allowed);
    }

    @Override
    public void clearAllowedTools() {
        permissionResolver.clearAllowedTools();
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
        permissionResolver.setPlanMode(planMode);
    }

    /**
     * Check if plan mode is currently active.
     *
     * @return true if in plan mode
     */
    public boolean isPlanMode() {
        return permissionResolver.isPlanMode();
    }

    /**
     * Set a tool-specific permission override.
     *
     * @param toolName the tool name
     * @param permission the permission level
     */
    public void setToolPermission(String toolName, ToolPermission permission) {
        permissionResolver.setToolPermission(toolName, permission);
    }

    /**
     * Set a default permission for all tools with the given side-effect classification.
     *
     * @param sideEffect the side-effect category
     * @param permission the permission level
     */
    public void setDefaultPermission(ToolSideEffect sideEffect, ToolPermission permission) {
        permissionResolver.setDefaultPermission(sideEffect, permission);
    }

    @Override
    public Mono<ToolResult> execute(String toolName, Map<String, Object> input) {
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
                            if (result != null) {
                                toolSpan.setStatus(
                                        !result.isError(),
                                        result.isError() ? result.content() : "OK");
                            } else {
                                toolSpan.setStatus(false, "empty result");
                            }
                            toolSpan.end();
                        })
                .doOnError(
                        e -> {
                            toolSpan.setStatus(false, e.getMessage());
                            toolSpan.end();
                        });
    }

    /** Build a composite circuit-breaker key scoped to both tool name and session. */
    private String circuitBreakerKey(
            String toolName, reactor.util.context.ContextView contextView) {
        if (contextView.hasKey(CONTEXT_KEY)) {
            ToolContext ctx = contextView.get(CONTEXT_KEY);
            if (ctx.sessionId() != null) {
                return toolName + "::" + ctx.sessionId();
            }
        }
        return toolName;
    }

    private Mono<ToolResult> executeInternal(
            String toolName, Map<String, Object> input, Duration timeout) {
        return Mono.deferContextual(
                contextView -> {
                    // 1. Circuit breaker check
                    String cbKey = circuitBreakerKey(toolName, contextView);
                    if (!circuitBreakerTracker.allowCall(cbKey)) {
                        int failures = circuitBreakerTracker.getFailureCount(cbKey);
                        return Mono.just(
                                ToolResultSanitizer.errorResult(
                                        toolName,
                                        "Tool '"
                                                + toolName
                                                + "' is circuit-broken after "
                                                + failures
                                                + " consecutive failures. Reset by"
                                                + " successful execution or agent"
                                                + " restart."));
                    }

                    // 2. Active skill tool constraints check
                    if (!permissionResolver.checkActiveToolConstraints(toolName)) {
                        return Mono.just(
                                ToolResultSanitizer.errorResult(
                                        toolName,
                                        "Tool '"
                                                + toolName
                                                + "' is not allowed by the active skill."
                                                + " Allowed tools: "
                                                + permissionResolver.getActiveToolConstraints()));
                    }

                    // 3. Plan mode check
                    try {
                        permissionResolver.checkPlanModeRestriction(toolName);
                    } catch (PlanModeViolationException e) {
                        return Mono.just(ToolResultSanitizer.errorResult(toolName, e.getMessage()));
                    }

                    // 4. Tool lookup
                    ToolDefinition definition = registry.get(toolName).orElse(null);
                    if (definition == null) {
                        return Mono.just(
                                ToolResultSanitizer.errorResult(
                                        toolName, "Unknown tool: " + toolName));
                    }

                    // 5. Get tool handler instance
                    Object instance = registry.getToolInstance(toolName);
                    if (!(instance instanceof ToolHandler handler)) {
                        return Mono.just(
                                ToolResultSanitizer.errorResult(
                                        toolName,
                                        "Tool '" + toolName + "' has no executable handler"));
                    }

                    // 6. Permission guard check
                    return permissionResolver
                            .getPermissionGuard()
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
                                            return Mono.just(
                                                    ToolResultSanitizer.errorResult(toolName, msg));
                                        }
                                        // 7. PRE_TOOL guardrail
                                        return evaluatePreToolGuardrail(toolName, input)
                                                .flatMap(
                                                        preDecision -> {
                                                            if (preDecision.action()
                                                                    == GuardrailDecision.Action
                                                                            .DENY) {
                                                                return Mono.just(
                                                                        ToolResultSanitizer
                                                                                .errorResult(
                                                                                        toolName,
                                                                                        "Guardrail denied: "
                                                                                                + preDecision
                                                                                                        .reason()));
                                                            }
                                                            // Use modified args if MODIFY
                                                            Map<String, Object> effectiveInput =
                                                                    input;
                                                            if (preDecision.action()
                                                                            == GuardrailDecision
                                                                                    .Action.MODIFY
                                                                    && preDecision.modifiedPayload()
                                                                            instanceof
                                                                            GuardrailPayload
                                                                                            .ToolInput
                                                                                    modified) {
                                                                effectiveInput = modified.args();
                                                            }
                                                            // 8. Execute tool via runner
                                                            Map<String, Object> finalInput =
                                                                    effectiveInput;
                                                            return invocationRunner
                                                                    .execute(
                                                                            toolName,
                                                                            handler,
                                                                            finalInput,
                                                                            timeout)
                                                                    .doOnNext(
                                                                            result ->
                                                                                    circuitBreakerTracker
                                                                                            .track(
                                                                                                    cbKey,
                                                                                                    result))
                                                                    .map(
                                                                            ToolResultSanitizer
                                                                                    ::sanitize)
                                                                    // 9. POST_TOOL guardrail
                                                                    .flatMap(
                                                                            result ->
                                                                                    evaluatePostToolGuardrail(
                                                                                                    toolName,
                                                                                                    result)
                                                                                            .map(
                                                                                                    postDecision -> {
                                                                                                        if (postDecision
                                                                                                                        .action()
                                                                                                                == GuardrailDecision
                                                                                                                        .Action
                                                                                                                        .DENY) {
                                                                                                            return ToolResultSanitizer
                                                                                                                    .errorResult(
                                                                                                                            toolName,
                                                                                                                            "Guardrail denied: "
                                                                                                                                    + postDecision
                                                                                                                                            .reason());
                                                                                                        }
                                                                                                        if (postDecision
                                                                                                                                .action()
                                                                                                                        == GuardrailDecision
                                                                                                                                .Action
                                                                                                                                .MODIFY
                                                                                                                && postDecision
                                                                                                                                .modifiedPayload()
                                                                                                                        instanceof
                                                                                                                        GuardrailPayload
                                                                                                                                        .ToolOutput
                                                                                                                                modified) {
                                                                                                            return modified
                                                                                                                    .result();
                                                                                                        }
                                                                                                        return result;
                                                                                                    }));
                                                        });
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
     * SYSTEM_CHANGE tools are executed serially in their original order.
     *
     * @param invocations the tool invocations to execute
     * @return a Flux emitting results in original invocation order
     */
    public Flux<ToolResult> executePartitioned(List<ToolInvocation> invocations) {
        return Mono.defer(
                        () -> {
                            var readInvocations = new ArrayList<ToolInvocation>();
                            var writeInvocations = new ArrayList<ToolInvocation>();
                            for (var inv : invocations) {
                                var sideEffect =
                                        permissionResolver.resolveSideEffect(inv.toolName());
                                if (sideEffect == ToolSideEffect.READ_ONLY) {
                                    readInvocations.add(inv);
                                } else {
                                    writeInvocations.add(inv);
                                }
                            }

                            var results = new LinkedHashMap<ToolInvocation, ToolResult>();
                            Mono<Void> readPhase = Mono.empty();
                            if (!readInvocations.isEmpty()) {
                                readPhase =
                                        Flux.fromIterable(readInvocations)
                                                .flatMap(
                                                        inv ->
                                                                approvalFlow
                                                                        .approveIfNeeded(inv)
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

                            Mono<Void> writePhase =
                                    Flux.fromIterable(writeInvocations)
                                            .concatMap(
                                                    inv ->
                                                            approvalFlow
                                                                    .approveIfNeeded(inv)
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

                            return readPhase.then(writePhase).thenReturn(results);
                        })
                .flatMapMany(
                        results ->
                                Flux.fromIterable(invocations)
                                        .map(
                                                inv ->
                                                        results.getOrDefault(
                                                                inv,
                                                                ToolResultSanitizer.errorResult(
                                                                        inv.toolName(),
                                                                        "Tool result missing"))));
    }

    @Override
    public ToolSideEffect resolveSideEffect(String toolName) {
        return permissionResolver.resolveSideEffect(toolName);
    }

    @Override
    public Mono<ToolResult> executeSingle(ToolInvocation invocation) {
        return approvalFlow.approveIfNeeded(invocation);
    }

    @Override
    public void registerToolInstance(String toolName, Object instance) {
        registry.registerInstance(toolName, instance);
    }

    @Override
    public void setToolMetadata(String toolName, java.util.Map<String, Object> metadata) {
        registry.setToolMetadata(toolName, metadata);
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public void resetCircuitBreaker() {
        circuitBreakerTracker.reset();
    }

    @Override
    public void resetCircuitBreaker(String toolName) {
        circuitBreakerTracker.reset(toolName);
    }

    // ---- Guardrail helpers ----

    private Mono<GuardrailDecision> evaluatePreToolGuardrail(
            String toolName, Map<String, Object> input) {
        if (guardrailChain == null) {
            log.warn(
                    "GuardrailChain is null — PRE_TOOL guardrail evaluation skipped for tool: {}",
                    toolName);
            return Mono.just(GuardrailDecision.allow("no-guardrail"));
        }
        Map<String, Object> metadata = registry.getToolMetadata(toolName);
        return Mono.deferContextual(
                contextView -> {
                    String agentName = resolveAgentName(contextView);
                    return guardrailChain.evaluate(
                            new GuardrailContext(
                                    GuardrailPhase.PRE_TOOL,
                                    agentName,
                                    toolName,
                                    new GuardrailPayload.ToolInput(toolName, input),
                                    Map.copyOf(metadata)));
                });
    }

    private Mono<GuardrailDecision> evaluatePostToolGuardrail(String toolName, ToolResult result) {
        if (guardrailChain == null) {
            log.warn(
                    "GuardrailChain is null — POST_TOOL guardrail evaluation skipped for tool: {}",
                    toolName);
            return Mono.just(GuardrailDecision.allow("no-guardrail"));
        }
        return Mono.deferContextual(
                contextView -> {
                    String agentName = resolveAgentName(contextView);
                    return guardrailChain.evaluate(
                            new GuardrailContext(
                                    GuardrailPhase.POST_TOOL,
                                    agentName,
                                    toolName,
                                    new GuardrailPayload.ToolOutput(toolName, result),
                                    Map.of()));
                });
    }

    /** Extract agent name from Reactor context, falling back to agentId or {@code "agent"}. */
    private String resolveAgentName(reactor.util.context.ContextView contextView) {
        if (contextView.hasKey(CONTEXT_KEY)) {
            ToolContext ctx = contextView.get(CONTEXT_KEY);
            if (ctx.agentId() != null && !ctx.agentId().isBlank()) {
                return ctx.agentId();
            }
        }
        return "agent";
    }
}
