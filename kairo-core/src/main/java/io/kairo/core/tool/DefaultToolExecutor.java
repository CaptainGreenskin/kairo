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

import io.kairo.api.event.KairoEventBus;
import io.kairo.api.exception.PlanModeViolationException;
import io.kairo.api.guardrail.*;
import io.kairo.api.tool.*;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
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

    private static final int DEFAULT_MAX_TOOL_RESULT_CHARS = 20_000;
    static final int MAX_TOOL_RESULT_CHARS = resolveMaxToolResultChars();

    private static int resolveMaxToolResultChars() {
        String env = System.getenv("KAIRO_TOOL_RESULT_MAX_CHARS");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_TOOL_RESULT_CHARS;
    }

    /** Apply semantic compression to tool output exceeding {@link #MAX_TOOL_RESULT_CHARS}. */
    static ToolResult applyResultBudget(ToolResult result) {
        if (result == null || result.content() == null) return result;
        String content = result.content();
        if (content.length() <= MAX_TOOL_RESULT_CHARS) return result;
        // Use semantic compression instead of hard truncation
        String compressed = ToolOutputCompressor.compress(content, MAX_TOOL_RESULT_CHARS);
        return ToolResult.of(result.toolUseId(), compressed, result.isError(), result.metadata());
    }

    private final DefaultToolRegistry registry;
    private final Tracer tracer;
    private final ToolPermissionResolver permissionResolver;
    private final ToolCircuitBreakerTracker circuitBreakerTracker;
    private final ToolInvocationRunner invocationRunner;
    private final ToolApprovalFlow approvalFlow;
    private final GuardrailChain guardrailChain; // nullable — backward compatible
    @Nullable private final KairoEventBus eventBus;

    /** Reactor Context key used to propagate {@link ToolContext} through the reactive pipeline. */
    public static final Class<ToolContext> CONTEXT_KEY = ToolContext.class;

    /**
     * Reactor Context key used to propagate the active agent {@link Span} from {@code
     * DefaultReActAgent} to tool executions. Tool spans use this as their explicit parent because
     * Reactor schedulers can leak/strand thread-local OTel context.
     */
    public static final Class<Span> SPAN_CONTEXT_KEY = Span.class;

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
        this(
                registry,
                permissionGuard,
                tracer,
                shutdownManager,
                circuitBreakerThreshold,
                guardrailChain,
                null);
    }

    /**
     * Create a new executor with all options including event bus for observability.
     *
     * @param registry the tool registry
     * @param permissionGuard the permission guard
     * @param tracer the tracer (null defaults to NoopTracer)
     * @param shutdownManager the shutdown manager (null creates a new instance)
     * @param circuitBreakerThreshold consecutive failures before a tool is circuit-broken
     * @param guardrailChain the guardrail chain (null skips guardrail evaluation)
     * @param eventBus optional event bus to publish circuit breaker state transition events
     */
    public DefaultToolExecutor(
            DefaultToolRegistry registry,
            PermissionGuard permissionGuard,
            Tracer tracer,
            GracefulShutdownManager shutdownManager,
            int circuitBreakerThreshold,
            GuardrailChain guardrailChain,
            @Nullable KairoEventBus eventBus) {
        this.registry = registry;
        this.tracer = tracer != null ? tracer : new io.kairo.api.tracing.NoopTracer();
        GracefulShutdownManager sm =
                shutdownManager != null ? shutdownManager : new GracefulShutdownManager();

        this.permissionResolver = new ToolPermissionResolver(permissionGuard, registry);
        this.circuitBreakerTracker =
                new ToolCircuitBreakerTracker(circuitBreakerThreshold, eventBus);
        this.invocationRunner = new ToolInvocationRunner(this.tracer, sm);
        this.approvalFlow = new ToolApprovalFlow(permissionResolver, this);
        this.guardrailChain = guardrailChain;
        this.eventBus = eventBus;
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
        return Mono.deferContextual(
                ctxView -> {
                    Span parent =
                            ctxView.hasKey(SPAN_CONTEXT_KEY) ? ctxView.get(SPAN_CONTEXT_KEY) : null;
                    Span toolSpan = tracer.startToolSpan(parent, toolName, input);
                    long startMs = System.currentTimeMillis();
                    return executeInternal(toolName, input, timeout)
                            .map(DefaultToolExecutor::applyResultBudget)
                            .flatMap(result -> enforceBudget(result, toolName))
                            .doOnSuccess(
                                    result -> {
                                        Duration elapsed =
                                                Duration.ofMillis(
                                                        System.currentTimeMillis() - startMs);
                                        if (result != null) {
                                            String content = result.content();
                                            if (content != null) {
                                                String preview =
                                                        content.length() <= 4000
                                                                ? content
                                                                : content.substring(0, 4000) + "…";
                                                toolSpan.setAttribute(
                                                        "langfuse.observation.output", preview);
                                                toolSpan.setAttribute("output.value", preview);
                                                toolSpan.setAttribute(
                                                        "tool.output.length",
                                                        (long) content.length());
                                            }
                                            tracer.recordToolResult(
                                                    toolSpan, toolName, !result.isError(), elapsed);
                                            toolSpan.setStatus(
                                                    !result.isError(),
                                                    result.isError() ? result.content() : "OK");
                                        } else {
                                            tracer.recordToolResult(
                                                    toolSpan, toolName, false, elapsed);
                                            toolSpan.setStatus(false, "empty result");
                                        }
                                        toolSpan.end();
                                    })
                            .doOnError(
                                    e -> {
                                        Duration elapsed =
                                                Duration.ofMillis(
                                                        System.currentTimeMillis() - startMs);
                                        tracer.recordToolResult(toolSpan, toolName, false, elapsed);
                                        tracer.recordException(toolSpan, e);
                                        toolSpan.setStatus(false, e.getMessage());
                                        toolSpan.end();
                                    });
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
                    if (instance == null) {
                        return Mono.just(
                                ToolResultSanitizer.errorResult(
                                        toolName,
                                        "Tool '" + toolName + "' has no executable handler"));
                    }
                    // Resolve to SyncTool, StreamingTool, or legacy ToolHandler
                    if (!(instance instanceof SyncTool)
                            && !(instance instanceof StreamingTool)
                            && !(instance instanceof ToolHandler)) {
                        return Mono.just(
                                ToolResultSanitizer.errorResult(
                                        toolName,
                                        "Tool '"
                                                + toolName
                                                + "' does not implement SyncTool,"
                                                + " StreamingTool, or ToolHandler"));
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
                                                                            instance,
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

    // ---- Output Budget enforcement ----

    /**
     * Enforce output budget limits using the {@link OutputBudgetTracker} from Reactor Context. If
     * no tracker is present (e.g. direct executor usage without agent loop), passes through.
     */
    private Mono<ToolResult> enforceBudget(ToolResult result, String toolName) {
        return Mono.deferContextual(
                ctxView -> {
                    if (!ctxView.hasKey(OutputBudgetTracker.CONTEXT_KEY)) {
                        return Mono.just(result);
                    }
                    OutputBudgetTracker tracker = ctxView.get(OutputBudgetTracker.CONTEXT_KEY);
                    String content = result.content();
                    if (content == null || content.isEmpty()) {
                        return Mono.just(result);
                    }

                    long outputBytes = content.getBytes(StandardCharsets.UTF_8).length;

                    // Check per-tool limit
                    if (tracker.exceedsPerToolLimit(outputBytes)) {
                        long limit = tracker.config().maxPerToolBytes();
                        ToolResult truncated = truncateResult(result, limit);
                        tracker.consume(limit);
                        return Mono.just(truncated);
                    }

                    // Check per-turn limit
                    if (tracker.exceedsPerTurnLimit(outputBytes)) {
                        long remaining = tracker.remainingTurnBudget();
                        long effectiveLimit = Math.max(remaining, 1024); // at least 1KB visible
                        ToolResult truncated = truncateResult(result, effectiveLimit);
                        tracker.consume(effectiveLimit);
                        return Mono.just(truncated);
                    }

                    // Within budget — record consumption
                    tracker.consume(outputBytes);
                    return Mono.just(result);
                });
    }

    /**
     * Truncate a tool result to the given byte limit, persisting the full output to a spill file.
     */
    private ToolResult truncateResult(ToolResult result, long maxBytes) {
        String content = result.content();
        byte[] fullBytes = content.getBytes(StandardCharsets.UTF_8);
        int truncateAt = (int) Math.min(fullBytes.length, maxBytes);

        // Find a safe UTF-8 boundary
        while (truncateAt > 0 && (fullBytes[truncateAt - 1] & 0xC0) == 0x80) {
            truncateAt--;
        }

        String visible = new String(fullBytes, 0, truncateAt, StandardCharsets.UTF_8);
        visible +=
                "\n\n... (output truncated: "
                        + fullBytes.length
                        + " bytes total, "
                        + truncateAt
                        + " bytes shown)";

        // Persist full output best-effort
        URI spillUri = persistFullOutput(result.toolUseId(), content);

        ToolOutput.Truncated truncatedOutput =
                new ToolOutput.Truncated(
                        visible, fullBytes.length, java.util.Optional.ofNullable(spillUri));

        List<Hint> hints = new ArrayList<>(result.hints());
        hints.add(
                new Hint(
                        Hint.HintLevel.INFO,
                        "Output truncated by budget policy."
                                + (spillUri != null ? " Full output: " + spillUri : ""),
                        java.util.Optional.empty()));

        return new ToolResult(
                result.toolUseId(), truncatedOutput, result.outcome(), hints, result.metadata());
    }

    /**
     * Persist full output to .kairo/tool-output/ for later retrieval. Returns the file URI on
     * success, null on failure.
     */
    private URI persistFullOutput(String toolUseId, String content) {
        try {
            Path spillDir = Path.of(System.getProperty("user.dir"), ".kairo", "tool-output");
            Files.createDirectories(spillDir);
            String safeId =
                    toolUseId != null ? toolUseId.replaceAll("[^a-zA-Z0-9_\\-]", "_") : "unknown";
            String fileName = safeId + "_" + System.currentTimeMillis() + ".txt";
            Path spillFile = spillDir.resolve(fileName);
            Files.writeString(spillFile, content, StandardCharsets.UTF_8);
            log.debug("Persisted truncated tool output to: {}", spillFile);
            return spillFile.toUri();
        } catch (Exception e) {
            log.warn(
                    "Failed to persist truncated tool output for {}: {}",
                    toolUseId,
                    e.getMessage());
            return null;
        }
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
