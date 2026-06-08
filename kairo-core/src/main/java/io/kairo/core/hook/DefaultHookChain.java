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
package io.kairo.core.hook;

import io.kairo.api.hook.*;
import io.kairo.api.message.Msg;
import io.kairo.api.tracing.NoopTracer;
import io.kairo.api.tracing.ObservationData;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.health.HookChainObserver;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link HookChain} that discovers annotated methods on registered
 * handler objects via reflection and invokes them in order.
 *
 * <p>Hook handlers are POJOs with methods annotated with lifecycle annotations such as {@link
 * PreReasoning}, {@link PostReasoning}, {@link PreActing}, {@link PostActing}, {@link PreCompact},
 * and {@link PostCompact}.
 */
public class DefaultHookChain implements HookChain {

    private static final Logger log = LoggerFactory.getLogger(DefaultHookChain.class);

    /**
     * Reactor Context key for the diagnostics event recorder. The value stored is a {@code
     * Consumer<String>} that records event types.
     */
    public static final String DIAGNOSTICS_RECORDER_KEY = "kairo.diagnostics.recorder";

    /**
     * Reactor Context key used to propagate the parent {@link Span} from upstream (agent /
     * iteration) into hook firings so child spans nest under the correct parent in the trace tree.
     * Matches the pattern in {@code DefaultToolExecutor.SPAN_CONTEXT_KEY} so existing callers that
     * already populate {@code Context.put(Span.class, …)} get hook spans wired for free.
     */
    public static final Class<Span> SPAN_CONTEXT_KEY = Span.class;

    private final List<Object> handlers = new CopyOnWriteArrayList<>();
    private final List<ExternalHookExecutor> externalExecutors = new CopyOnWriteArrayList<>();
    private final List<ExternalHookBinding> externalBindings = new CopyOnWriteArrayList<>();

    private final Tracer tracer;
    private final Map<String, AtomicLong> firedByPhase = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failuresByPhase = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> decisionsByOutcome = new ConcurrentHashMap<>();
    private final AtomicLong externalHookFailures = new AtomicLong();
    private final AtomicLong totalDurationNanos = new AtomicLong();
    private volatile io.kairo.api.hook.HookSessionContext sessionContext;

    /** Backward-compatible constructor — no tracer, so hook spans are NoopSpan (no-op). */
    public DefaultHookChain() {
        this(NoopTracer.INSTANCE);
    }

    /**
     * Construct a chain that emits one child span per hook firing via {@code
     * tracer.startHookSpan(parent, phase, hookName)}. Pass {@link NoopTracer#INSTANCE} for tests or
     * production runs without an observability backend — span calls become no-ops.
     */
    public DefaultHookChain(Tracer tracer) {
        this.tracer = tracer == null ? NoopTracer.INSTANCE : tracer;
    }

    /**
     * Set the per-session hook context. Called by the agent at session start; cleared at session
     * end. Hook methods with a second {@link io.kairo.api.hook.HookSessionContext} parameter
     * receive this automatically.
     */
    public void setSessionContext(io.kairo.api.hook.HookSessionContext context) {
        this.sessionContext = context;
    }

    @Override
    public void register(Object hookHandler) {
        if (hookHandler != null) {
            handlers.add(hookHandler);
            log.debug("Registered hook handler: {}", hookHandler.getClass().getSimpleName());
        }
    }

    @Override
    public void unregister(Object hookHandler) {
        handlers.remove(hookHandler);
        log.debug("Unregistered hook handler: {}", hookHandler.getClass().getSimpleName());
    }

    /** Returns an unmodifiable snapshot of the currently registered hook handlers. */
    public List<Object> getRegisteredHandlers() {
        return Collections.unmodifiableList(new ArrayList<>(handlers));
    }

    @Override
    public <T> Mono<T> firePreReasoning(T event) {
        return fireEvent(event, PreReasoning.class);
    }

    @Override
    public <T> Mono<T> firePostReasoning(T event) {
        return withDiagnosticsRecording(fireEvent(event, PostReasoning.class), "POST_REASONING");
    }

    @Override
    public <T> Mono<T> firePreActing(T event) {
        return fireEvent(event, PreActing.class);
    }

    @Override
    public <T> Mono<T> firePostActing(T event) {
        return withDiagnosticsRecording(fireEvent(event, PostActing.class), "POST_ACTING");
    }

    @Override
    public <T> Mono<T> firePreCompact(T event) {
        return fireEvent(event, PreCompact.class);
    }

    @Override
    public <T> Mono<T> firePostCompact(T event) {
        return fireEvent(event, PostCompact.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Mono<HookResult<T>> firePreActingWithResult(T event) {
        return fireEventWithResult(event, PreActing.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Mono<HookResult<T>> firePostActingWithResult(T event) {
        return fireEventWithResult(event, PostActing.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Mono<HookResult<T>> firePreReasoningWithResult(T event) {
        return fireEventWithResult(event, PreReasoning.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Mono<HookResult<T>> firePostReasoningWithResult(T event) {
        return fireEventWithResult(event, PostReasoning.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Mono<HookResult<T>> firePreCompactWithResult(T event) {
        return fireEventWithResult(event, PreCompact.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Mono<HookResult<T>> firePostCompactWithResult(T event) {
        return fireEventWithResult(event, PostCompact.class);
    }

    @Override
    public <T> Mono<T> fireOnSessionStart(T event) {
        return withDiagnosticsRecording(fireEvent(event, OnSessionStart.class), "SESSION_START");
    }

    @Override
    public <T> Mono<T> fireOnSessionEnd(T event) {
        return withDiagnosticsRecording(fireEvent(event, OnSessionEnd.class), "SESSION_END");
    }

    @Override
    public <T> Mono<T> fireOnToolResult(T event) {
        return withDiagnosticsRecording(fireEvent(event, OnToolResult.class), "TOOL_RESULT");
    }

    @Override
    public <T> Mono<T> fireOnUserPromptSubmit(T event) {
        return withDiagnosticsRecording(
                fireEvent(event, OnUserPromptSubmit.class), "USER_PROMPT_SUBMIT");
    }

    @Override
    public <T> Mono<T> fireOnNotification(T event) {
        return withDiagnosticsRecording(fireEvent(event, OnNotification.class), "NOTIFICATION");
    }

    @Override
    public <T> Mono<T> fireOnSubagentStart(T event) {
        return withDiagnosticsRecording(fireEvent(event, OnSubagentStart.class), "SUBAGENT_START");
    }

    @Override
    public <T> Mono<T> fireOnSubagentStop(T event) {
        return withDiagnosticsRecording(fireEvent(event, OnSubagentStop.class), "SUBAGENT_STOP");
    }

    @Override
    public <T> Mono<T> firePreComplete(T event) {
        return fireEvent(event, PreComplete.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Mono<HookResult<T>> firePreCompleteWithResult(T event) {
        return fireEventWithResult(event, PreComplete.class);
    }

    /**
     * Fire all {@link OnError}-annotated methods with the given error event. Best-effort: errors
     * thrown by handlers are logged and swallowed so hook failures never mask the original error.
     */
    public Mono<Void> fireOnError(AgentErrorEvent event) {
        return fireEvent(event, OnError.class).then();
    }

    // ── External hook executor management ───────────────────────────────────

    /**
     * Register an external hook executor (command, http, etc.).
     *
     * @param executor the executor to register
     */
    public void registerExecutor(ExternalHookExecutor executor) {
        if (executor != null) {
            externalExecutors.add(executor);
            log.debug("Registered external hook executor: {}", executor.type());
        }
    }

    /**
     * Register an external hook binding (phase + config from settings file).
     *
     * @param binding the binding to register
     */
    public void registerExternalBinding(ExternalHookBinding binding) {
        if (binding != null) {
            externalBindings.add(binding);
            log.debug(
                    "Registered external hook binding: {} → {}",
                    binding.phase(),
                    binding.config().type());
        }
    }

    /** Remove all external bindings (used when config is reloaded). */
    public void clearExternalBindings() {
        externalBindings.clear();
    }

    // ── Generic phase dispatch (v0.11+) ─────────────────────────────────────

    @Override
    public <T extends HookEvent> Mono<T> firePhase(HookPhase phase, T event) {
        return Mono.defer(
                () -> {
                    // 1. Fire in-process @HookHandler(phase) methods
                    Mono<T> inProcessMono = firePhaseInProcess(phase, event);

                    // 2. Fire matching external hooks
                    String matcherTarget = extractMatcherTarget(event);
                    List<ExternalHookBinding> matching = findMatchingBindings(phase, matcherTarget);
                    if (matching.isEmpty()) {
                        return withDiagnosticsRecording(inProcessMono, phase.name());
                    }

                    // Chain: in-process first, then external hooks in parallel
                    return withDiagnosticsRecording(
                            inProcessMono.flatMap(
                                    updatedEvent -> fireExternalHooks(updatedEvent, matching)),
                            phase.name());
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends HookEvent> Mono<HookResult<T>> firePhaseWithResult(HookPhase phase, T event) {
        return Mono.defer(
                () -> {
                    // 1. Fire in-process handlers with result
                    Mono<HookResult<T>> inProcessMono = firePhaseInProcessWithResult(phase, event);

                    // 2. Fire matching external hooks
                    String matcherTarget = extractMatcherTarget(event);
                    List<ExternalHookBinding> matching = findMatchingBindings(phase, matcherTarget);
                    if (matching.isEmpty()) {
                        return inProcessMono;
                    }

                    return inProcessMono.flatMap(
                            inResult -> {
                                if (inResult.decision() == HookResult.Decision.ABORT) {
                                    return Mono.just(inResult);
                                }
                                return fireExternalHooksWithResult(
                                                inResult.event(), matching, inResult)
                                        .map(
                                                extResult ->
                                                        mergeResults(
                                                                inResult,
                                                                (HookResult<T>) extResult));
                            });
                });
    }

    @SuppressWarnings("unchecked")
    private <T extends HookEvent> Mono<T> firePhaseInProcess(HookPhase phase, T event) {
        List<AnnotatedMethod> methods = findMethodsForPhase(phase);
        if (methods.isEmpty()) {
            return Mono.just(event);
        }

        return Mono.deferContextual(
                ctx -> {
                    Span parent = parentSpanFromContext(ctx);
                    T current = event;
                    for (AnnotatedMethod am : methods) {
                        if (isCancelled(current)) break;
                        Span span = beginHookSpan(parent, phase.name(), am);
                        long start = System.nanoTime();
                        try {
                            Object result = invokeHookMethod(am, current);
                            recordSuccess(
                                    span, phase.name(), am, "CONTINUE", System.nanoTime() - start);
                            if (result != null && event.getClass().isInstance(result)) {
                                current = (T) result;
                            }
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            recordFailure(
                                    span,
                                    phase.name(),
                                    am,
                                    cause != null ? cause : e,
                                    System.nanoTime() - start);
                            log.error(
                                    "Hook method {}#{} threw exception",
                                    am.handler().getClass().getSimpleName(),
                                    am.method().getName(),
                                    cause);
                            return Mono.error(cause != null ? cause : e);
                        } catch (IllegalAccessException e) {
                            recordFailure(span, phase.name(), am, e, System.nanoTime() - start);
                            log.error(
                                    "Hook method {}#{} is not accessible",
                                    am.handler().getClass().getSimpleName(),
                                    am.method().getName(),
                                    e);
                            return Mono.error(e);
                        }
                    }
                    return Mono.just(current);
                });
    }

    @SuppressWarnings("unchecked")
    private <T extends HookEvent> Mono<HookResult<T>> firePhaseInProcessWithResult(
            HookPhase phase, T event) {
        List<AnnotatedMethod> methods = findMethodsForPhase(phase);
        if (methods.isEmpty()) {
            return Mono.just(HookResult.proceed(event));
        }

        return Mono.deferContextual(
                ctx -> {
                    Span parent = parentSpanFromContext(ctx);
                    T current = event;
                    HookResult.Decision winningDecision = HookResult.Decision.CONTINUE;
                    String winningReason = null;

                    for (AnnotatedMethod am : methods) {
                        if (isCancelled(current)) break;
                        Span span = beginHookSpan(parent, phase.name(), am);
                        long start = System.nanoTime();
                        try {
                            Object result = invokeHookMethod(am, current);
                            String decisionLabel = "CONTINUE";
                            if (result instanceof HookResult<?> hr) {
                                HookResult<T> typed = (HookResult<T>) hr;
                                current = typed.event();
                                decisionLabel = typed.decision().name();
                                if (typed.decision() == HookResult.Decision.ABORT) {
                                    recordSuccess(
                                            span,
                                            phase.name(),
                                            am,
                                            decisionLabel,
                                            System.nanoTime() - start);
                                    return Mono.just(typed);
                                }
                                if (typed.decision().priority() > winningDecision.priority()) {
                                    winningDecision = typed.decision();
                                    if (typed.reason() != null) winningReason = typed.reason();
                                }
                            } else if (result != null && event.getClass().isInstance(result)) {
                                current = (T) result;
                            }
                            recordSuccess(
                                    span,
                                    phase.name(),
                                    am,
                                    decisionLabel,
                                    System.nanoTime() - start);
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            recordFailure(
                                    span,
                                    phase.name(),
                                    am,
                                    cause != null ? cause : e,
                                    System.nanoTime() - start);
                            log.error("Hook threw exception", cause);
                            return Mono.error(cause != null ? cause : e);
                        } catch (IllegalAccessException e) {
                            recordFailure(span, phase.name(), am, e, System.nanoTime() - start);
                            return Mono.error(e);
                        }
                    }

                    return Mono.just(
                            new HookResult<>(
                                    current,
                                    winningDecision,
                                    null,
                                    null,
                                    winningReason,
                                    null,
                                    null));
                });
    }

    @SuppressWarnings("unchecked")
    private <T extends HookEvent> Mono<T> fireExternalHooks(
            T event, List<ExternalHookBinding> bindings) {
        return Flux.fromIterable(bindings)
                .flatMap(
                        binding -> {
                            ExternalHookExecutor executor = findExecutor(binding.config().type());
                            if (executor == null) {
                                log.warn("No executor for hook type: {}", binding.config().type());
                                return Mono.just(HookResult.proceed(event));
                            }
                            return executor.execute(event, binding.config())
                                    .onErrorResume(
                                            e -> {
                                                recordExternalHookFailure(
                                                        binding.phase().name(),
                                                        externalHookId(binding),
                                                        e);
                                                log.warn(
                                                        "External hook failed: {}", e.getMessage());
                                                return Mono.just(HookResult.proceed(event));
                                            });
                        })
                .reduce(
                        event,
                        (acc, result) -> {
                            if (result.decision() == HookResult.Decision.ABORT) {
                                return acc; // ABORT handled at WithResult level
                            }
                            return result.event() != null ? (T) result.event() : acc;
                        });
    }

    @SuppressWarnings("unchecked")
    private <T extends HookEvent> Mono<HookResult<T>> fireExternalHooksWithResult(
            T event, List<ExternalHookBinding> bindings, HookResult<T> baseline) {
        return Flux.fromIterable(bindings)
                .flatMap(
                        binding -> {
                            ExternalHookExecutor executor = findExecutor(binding.config().type());
                            if (executor == null) {
                                return Mono.just(HookResult.proceed(event));
                            }
                            return executor.<T>execute(event, binding.config())
                                    .onErrorResume(
                                            e -> {
                                                recordExternalHookFailure(
                                                        binding.phase().name(),
                                                        externalHookId(binding),
                                                        e);
                                                log.warn(
                                                        "External hook failed: {}", e.getMessage());
                                                return Mono.just(HookResult.proceed(event));
                                            });
                        })
                .reduce(baseline, (acc, result) -> mergeResults(acc, (HookResult<T>) result));
    }

    /**
     * Derive a span-identifier for an external hook binding. Prefers the command / URL over the
     * generic type so dashboards show which concrete hook failed (e.g. {@code
     * command:./scripts/pre-tool.sh} rather than just {@code command}).
     */
    private String externalHookId(ExternalHookBinding binding) {
        if (binding == null || binding.config() == null) return "unknown";
        var cfg = binding.config();
        String type = cfg.type() == null ? "external" : cfg.type();
        String detail = cfg.command();
        if (detail == null || detail.isBlank()) detail = cfg.url();
        if (detail == null || detail.isBlank()) return type;
        return type + ":" + detail;
    }

    private <T> HookResult<T> mergeResults(HookResult<T> a, HookResult<T> b) {
        HookResult.Decision winner =
                a.decision().priority() >= b.decision().priority() ? a.decision() : b.decision();
        String reason = b.reason() != null ? b.reason() : a.reason();
        T event = b.event() != null ? b.event() : a.event();
        String context = b.hasInjectedContext() ? b.injectedContext() : a.injectedContext();
        java.util.Map<String, Object> input =
                b.hasModifiedInput() ? b.modifiedInput() : a.modifiedInput();
        Msg msg = b.hasInjectedMessage() ? b.injectedMessage() : a.injectedMessage();
        String source = b.hookSource() != null ? b.hookSource() : a.hookSource();

        if (a.decision() == HookResult.Decision.ABORT) return a;
        if (b.decision() == HookResult.Decision.ABORT) return b;

        return new HookResult<>(event, winner, context, input, reason, msg, source);
    }

    // ── Matcher filtering ───────────────────────────────────────────────────

    private List<ExternalHookBinding> findMatchingBindings(HookPhase phase, String matcherTarget) {
        List<ExternalHookBinding> result = new ArrayList<>();
        for (ExternalHookBinding binding : externalBindings) {
            if (binding.phase() != phase) continue;
            String matcher = binding.config().matcher();
            if (matcher == null || matcher.isEmpty() || matchesMatcher(matcher, matcherTarget)) {
                result.add(binding);
            }
        }
        return result;
    }

    static boolean matchesMatcher(String matcher, String target) {
        if (target == null || target.isEmpty()) return true;
        for (String part : matcher.split("\\|")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.equals(target)) return true;
            try {
                if (Pattern.matches(trimmed, target)) return true;
            } catch (Exception ignored) {
                // invalid regex, treat as literal
            }
        }
        return false;
    }

    private String extractMatcherTarget(HookEvent event) {
        if (event instanceof PreActingEvent e) return e.toolName();
        if (event instanceof PostActingEvent e) return e.toolName();
        if (event instanceof PostToolFailureEvent e) return e.toolName();
        if (event instanceof PermissionRequestEvent e) return e.toolName();
        if (event instanceof PermissionDeniedEvent e) return e.toolName();
        if (event instanceof ToolResultEvent e) return e.toolName();
        if (event instanceof SessionStartEvent) return "startup";
        if (event instanceof SetupEvent e) return e.mode();
        if (event instanceof NotificationEvent e) return e.notificationType();
        if (event instanceof SubagentStartEvent e) return e.agentType();
        if (event instanceof SubagentStopEvent e) return e.agentType();
        if (event instanceof ConfigChangeEvent e) return e.source();
        if (event instanceof StopFailureEvent e) return e.errorType();
        if (event instanceof InstructionsLoadedEvent e) return e.source();
        if (event instanceof FileChangedEvent e) return e.filePath();
        return null;
    }

    private ExternalHookExecutor findExecutor(String type) {
        for (ExternalHookExecutor executor : externalExecutors) {
            if (executor.type().equals(type)) return executor;
        }
        return null;
    }

    private List<AnnotatedMethod> findMethodsForPhase(HookPhase phase) {
        List<AnnotatedMethod> result = new ArrayList<>();
        for (Object handler : handlers) {
            for (Method method : handler.getClass().getMethods()) {
                if (!isHookMethod(method)) continue;
                HookHandler unified = method.getAnnotation(HookHandler.class);
                if (unified != null && unified.value() == phase) {
                    method.setAccessible(true);
                    result.add(new AnnotatedMethod(handler, method, unified.order()));
                }
            }
        }
        result.sort(Comparator.comparingInt(AnnotatedMethod::order));
        return result;
    }

    /**
     * Fire an event through all handlers that have methods annotated with the given annotation.
     * Methods are sorted by their {@code order()} value and invoked sequentially. If the event has
     * a {@code cancelled()} method that returns true, the chain is short-circuited.
     *
     * @param event the event to fire
     * @param annotationType the annotation to look for
     * @param <T> the event type
     * @return a Mono emitting the (possibly modified) event
     */
    @SuppressWarnings("unchecked")
    private <T> Mono<T> fireEvent(T event, Class<? extends Annotation> annotationType) {
        return Mono.deferContextual(
                ctx -> {
                    List<AnnotatedMethod> methods = findAnnotatedMethods(annotationType);
                    if (methods.isEmpty()) {
                        return Mono.just(event);
                    }
                    Span parent = parentSpanFromContext(ctx);
                    String phaseLabel = phaseLabelFor(annotationType);

                    T current = event;
                    for (AnnotatedMethod am : methods) {
                        // Check if event has been cancelled
                        if (isCancelled(current)) {
                            log.debug(
                                    "Hook chain short-circuited by cancelled event at {}#{}",
                                    am.handler().getClass().getSimpleName(),
                                    am.method().getName());
                            break;
                        }

                        Span span = beginHookSpan(parent, phaseLabel, am);
                        long start = System.nanoTime();
                        try {
                            Object result = invokeHookMethod(am, current);
                            recordSuccess(
                                    span, phaseLabel, am, "CONTINUE", System.nanoTime() - start);
                            if (result != null && event.getClass().isInstance(result)) {
                                current = (T) result;
                            }
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            recordFailure(
                                    span,
                                    phaseLabel,
                                    am,
                                    cause != null ? cause : e,
                                    System.nanoTime() - start);
                            log.error(
                                    "Hook method {}#{} threw exception",
                                    am.handler().getClass().getSimpleName(),
                                    am.method().getName(),
                                    cause);
                            return Mono.error(cause != null ? cause : e);
                        } catch (IllegalAccessException e) {
                            recordFailure(span, phaseLabel, am, e, System.nanoTime() - start);
                            log.error(
                                    "Hook method {}#{} is not accessible",
                                    am.handler().getClass().getSimpleName(),
                                    am.method().getName(),
                                    e);
                            return Mono.error(e);
                        }
                    }
                    return Mono.just(current);
                });
    }

    /**
     * Fire an event and collect structured results with decision priority merge.
     *
     * <p>Decision priority: ABORT > SKIP > MODIFY > INJECT > CONTINUE. ABORT short-circuits the
     * chain. All other decisions continue processing but the highest-priority decision wins.
     * Multiple INJECT results accumulate messages in order.
     */
    @SuppressWarnings("unchecked")
    private <T> Mono<HookResult<T>> fireEventWithResult(
            T event, Class<? extends Annotation> annotationType) {
        return Mono.deferContextual(
                ctx -> {
                    List<AnnotatedMethod> methods = findAnnotatedMethods(annotationType);
                    if (methods.isEmpty()) {
                        return Mono.just(HookResult.proceed(event));
                    }
                    Span parent = parentSpanFromContext(ctx);
                    String phaseLabel = phaseLabelFor(annotationType);

                    T current = event;
                    HookResult.Decision winningDecision = HookResult.Decision.CONTINUE;
                    String winningReason = null;
                    String winningInjectedContext = null;
                    java.util.Map<String, Object> winningModifiedInput = null;
                    List<Msg> accumulatedInjectedMessages = new ArrayList<>();
                    String lastHookSource = null;

                    for (AnnotatedMethod am : methods) {
                        if (isCancelled(current)) {
                            break;
                        }

                        Span span = beginHookSpan(parent, phaseLabel, am);
                        long start = System.nanoTime();
                        try {
                            Object result = invokeHookMethod(am, current);
                            String decisionLabel = "CONTINUE";

                            if (result instanceof HookResult<?> hr) {
                                HookResult<T> typed = (HookResult<T>) hr;
                                current = typed.event();
                                decisionLabel = typed.decision().name();

                                // ABORT always short-circuits
                                if (typed.decision() == HookResult.Decision.ABORT) {
                                    log.debug(
                                            "Hook {}#{} aborted: {}",
                                            am.handler().getClass().getSimpleName(),
                                            am.method().getName(),
                                            typed.reason());
                                    recordSuccess(
                                            span,
                                            phaseLabel,
                                            am,
                                            decisionLabel,
                                            System.nanoTime() - start);
                                    return Mono.just(
                                            new HookResult<>(
                                                    current,
                                                    HookResult.Decision.ABORT,
                                                    winningInjectedContext,
                                                    winningModifiedInput,
                                                    typed.reason(),
                                                    typed.injectedMessage(),
                                                    typed.hookSource()));
                                }

                                // Priority merge: keep higher-priority decision
                                if (typed.decision().priority() > winningDecision.priority()) {
                                    winningDecision = typed.decision();
                                    if (typed.reason() != null) {
                                        winningReason = typed.reason();
                                    }
                                }

                                // Accumulate injected context
                                if (typed.hasInjectedContext()) {
                                    winningInjectedContext = typed.injectedContext();
                                }

                                // Accumulate modified input (last writer wins)
                                if (typed.hasModifiedInput()) {
                                    winningModifiedInput = typed.modifiedInput();
                                }

                                // Accumulate injected messages in order
                                if (typed.hasInjectedMessage()) {
                                    accumulatedInjectedMessages.add(typed.injectedMessage());
                                    lastHookSource = typed.hookSource();
                                }
                            } else if (result != null && event.getClass().isInstance(result)) {
                                // Hook returned a plain event — auto-wrap as CONTINUE
                                current = (T) result;
                            }
                            recordSuccess(
                                    span, phaseLabel, am, decisionLabel, System.nanoTime() - start);
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            recordFailure(
                                    span,
                                    phaseLabel,
                                    am,
                                    cause != null ? cause : e,
                                    System.nanoTime() - start);
                            log.error(
                                    "Hook method {}#{} threw exception",
                                    am.handler().getClass().getSimpleName(),
                                    am.method().getName(),
                                    cause);
                            return Mono.error(cause != null ? cause : e);
                        } catch (IllegalAccessException e) {
                            recordFailure(span, phaseLabel, am, e, System.nanoTime() - start);
                            log.error(
                                    "Hook method {}#{} is not accessible",
                                    am.handler().getClass().getSimpleName(),
                                    am.method().getName(),
                                    e);
                            return Mono.error(e);
                        }
                    }

                    // Build the final merged result
                    // For INJECT with accumulated messages, use the first message
                    // (caller should check accumulatedInjectedMessages via the list)
                    Msg finalInjectedMsg =
                            accumulatedInjectedMessages.isEmpty()
                                    ? null
                                    : accumulatedInjectedMessages.get(0);

                    HookResult<T> merged =
                            new HookResult<>(
                                    current,
                                    winningDecision,
                                    winningInjectedContext,
                                    winningModifiedInput,
                                    winningReason,
                                    finalInjectedMsg,
                                    lastHookSource);
                    return Mono.just(merged);
                });
    }

    /** Check if an event object has a {@code cancelled()} method that returns true. */
    private boolean isCancelled(Object event) {
        try {
            Method cancelledMethod = event.getClass().getMethod("cancelled");
            if (cancelledMethod.getReturnType() == boolean.class
                    || cancelledMethod.getReturnType() == Boolean.class) {
                return (boolean) cancelledMethod.invoke(event);
            }
        } catch (NoSuchMethodException ignored) {
            // Event doesn't have a cancelled() method
        } catch (Exception e) {
            log.warn("Error checking cancelled status on event: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Find all methods annotated with the given legacy annotation OR with the unified {@link
     * io.kairo.api.hook.HookHandler} tagged with the matching {@link HookPhase}, across all
     * registered handlers, sorted by their order value.
     */
    private List<AnnotatedMethod> findAnnotatedMethods(Class<? extends Annotation> annotationType) {
        HookPhase targetPhase = phaseFor(annotationType);
        List<AnnotatedMethod> result = new ArrayList<>();

        for (Object handler : handlers) {
            for (Method method : handler.getClass().getMethods()) {
                if (!isHookMethod(method)) {
                    continue;
                }

                Annotation legacy = method.getAnnotation(annotationType);
                if (legacy != null) {
                    method.setAccessible(true);
                    result.add(new AnnotatedMethod(handler, method, getOrder(legacy)));
                    continue;
                }

                if (targetPhase != null) {
                    HookHandler unified = method.getAnnotation(HookHandler.class);
                    if (unified != null && unified.value() == targetPhase) {
                        method.setAccessible(true);
                        result.add(new AnnotatedMethod(handler, method, unified.order()));
                    }
                }
            }
        }

        result.sort(Comparator.comparingInt(AnnotatedMethod::order));
        return result;
    }

    /**
     * Resolve a stable string label for the hook phase associated with the given annotation, used
     * as the {@code phase} attribute on hook spans. Falls back to the annotation simple name when
     * no canonical {@link HookPhase} matches (e.g. {@link OnError}).
     */
    private static String phaseLabelFor(Class<? extends Annotation> annotationType) {
        HookPhase p = phaseFor(annotationType);
        return p != null ? p.name() : annotationType.getSimpleName();
    }

    private static HookPhase phaseFor(Class<? extends Annotation> annotationType) {
        if (annotationType == PreReasoning.class) return HookPhase.PRE_REASONING;
        if (annotationType == PostReasoning.class) return HookPhase.POST_REASONING;
        if (annotationType == PreActing.class) return HookPhase.PRE_ACTING;
        if (annotationType == PostActing.class) return HookPhase.POST_ACTING;
        if (annotationType == PreCompact.class) return HookPhase.PRE_COMPACT;
        if (annotationType == PostCompact.class) return HookPhase.POST_COMPACT;
        if (annotationType == OnSessionStart.class) return HookPhase.SESSION_START;
        if (annotationType == OnSessionEnd.class) return HookPhase.SESSION_END;
        if (annotationType == OnToolResult.class) return HookPhase.TOOL_RESULT;
        if (annotationType == PreComplete.class) return HookPhase.PRE_COMPLETE;
        if (annotationType == OnUserPromptSubmit.class) return HookPhase.USER_PROMPT_SUBMIT;
        if (annotationType == OnNotification.class) return HookPhase.NOTIFICATION;
        if (annotationType == OnSubagentStart.class) return HookPhase.SUBAGENT_START;
        if (annotationType == OnSubagentStop.class) return HookPhase.SUBAGENT_STOP;
        return null;
    }

    /** Extract the order value from a hook annotation. */
    private int getOrder(Annotation annotation) {
        try {
            Method orderMethod = annotation.annotationType().getMethod("order");
            return (int) orderMethod.invoke(annotation);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Internal record holding a handler, its annotated method, and the execution order. */
    private record AnnotatedMethod(Object handler, Method method, int order) {}

    private static boolean isHookMethod(Method method) {
        int paramCount = method.getParameterCount();
        if (paramCount == 1) return true;
        if (paramCount == 2) {
            return io.kairo.api.hook.HookSessionContext.class.isAssignableFrom(
                    method.getParameterTypes()[1]);
        }
        return false;
    }

    private Object invokeHookMethod(AnnotatedMethod am, Object event)
            throws IllegalAccessException, InvocationTargetException {
        if (am.method().getParameterCount() == 2) {
            io.kairo.api.hook.HookSessionContext ctx =
                    sessionContext != null
                            ? sessionContext
                            : io.kairo.api.hook.NoopHookSessionContext.INSTANCE;
            return am.method().invoke(am.handler(), event, ctx);
        }
        return am.method().invoke(am.handler(), event);
    }

    // ==================== OBSERVATION ====================

    /**
     * Snapshot the in-memory counters as an immutable record. Mirrors the {@code
     * LlmBashClassifier.Stats} / {@code AgentMetricsCollector.AgentMetricsSummary} pattern so REPL
     * commands and tests can assert chain activity without standing up a full metrics backend.
     */
    public HookChainStats snapshot() {
        return new HookChainStats(
                snapshotMap(firedByPhase),
                snapshotMap(failuresByPhase),
                snapshotMap(decisionsByOutcome),
                externalHookFailures.get(),
                totalDurationNanos.get() / 1_000_000L);
    }

    private Map<String, Long> snapshotMap(Map<String, AtomicLong> source) {
        Map<String, Long> out = new HashMap<>(source.size());
        source.forEach((k, v) -> out.put(k, v.get()));
        return Map.copyOf(out);
    }

    /**
     * Programmatic observation snapshot for the hook chain. Returned by {@link #snapshot()};
     * exposes per-phase fire counts, per-phase failure counts, decision outcome counts (e.g.
     * CONTINUE / ABORT / SKIP / INJECT / MODIFY) and total wall-clock duration. Used by REPL {@code
     * :hooks} command and tests; values are point-in-time copies, not live views.
     */
    public record HookChainStats(
            Map<String, Long> firedByPhase,
            Map<String, Long> failuresByPhase,
            Map<String, Long> decisionsByOutcome,
            long externalHookFailures,
            long totalDurationMillis) {}

    /**
     * Resolve the parent {@link Span} for a hook firing by inspecting the Reactor Context, falling
     * back to {@code null} (the tracer's NoopSpan parent) when no upstream agent / iteration has
     * written one.
     */
    private Span parentSpanFromContext(reactor.util.context.ContextView ctx) {
        return ctx.hasKey(SPAN_CONTEXT_KEY) ? ctx.get(SPAN_CONTEXT_KEY) : null;
    }

    /**
     * Begin a hook span and return it. Caller is responsible for calling {@link
     * #recordSuccess(Span, String, AnnotatedMethod, String, long)} or {@link #recordFailure(Span,
     * String, AnnotatedMethod, Throwable, long)} with the elapsed nanos when the invocation
     * settles.
     */
    private Span beginHookSpan(Span parent, String phase, AnnotatedMethod am) {
        return tracer.startHookSpan(
                parent,
                phase,
                am.handler().getClass().getSimpleName() + "#" + am.method().getName());
    }

    private void recordSuccess(
            Span span, String phase, AnnotatedMethod am, String decision, long elapsedNanos) {
        firedByPhase.computeIfAbsent(phase, k -> new AtomicLong()).incrementAndGet();
        decisionsByOutcome.computeIfAbsent(decision, k -> new AtomicLong()).incrementAndGet();
        totalDurationNanos.addAndGet(elapsedNanos);
        long elapsedMs = elapsedNanos / 1_000_000L;

        Map<String, Object> meta = new HashMap<>();
        meta.put("hook.phase", phase);
        meta.put("hook.handler", am.handler().getClass().getName());
        meta.put("hook.method", am.method().getName());
        meta.put("hook.decision", decision);
        meta.put("hook.duration_ms", elapsedMs);

        tracer.recordObservation(
                span,
                ObservationData.builder()
                        .type(ObservationData.Type.SPAN)
                        .level(ObservationData.Level.DEFAULT)
                        .metadata(meta)
                        .build());
        span.setStatus(true, null);
        span.end();
        invokeObserver(
                o -> o.onHookFired(phase, decision, java.time.Duration.ofNanos(elapsedNanos)));
    }

    private void recordFailure(
            Span span, String phase, AnnotatedMethod am, Throwable error, long elapsedNanos) {
        firedByPhase.computeIfAbsent(phase, k -> new AtomicLong()).incrementAndGet();
        failuresByPhase.computeIfAbsent(phase, k -> new AtomicLong()).incrementAndGet();
        decisionsByOutcome.computeIfAbsent("ERROR", k -> new AtomicLong()).incrementAndGet();
        totalDurationNanos.addAndGet(elapsedNanos);
        long elapsedMs = elapsedNanos / 1_000_000L;

        Map<String, Object> meta = new HashMap<>();
        meta.put("hook.phase", phase);
        meta.put("hook.handler", am.handler().getClass().getName());
        meta.put("hook.method", am.method().getName());
        meta.put("hook.duration_ms", elapsedMs);

        tracer.recordObservation(
                span,
                ObservationData.builder()
                        .type(ObservationData.Type.SPAN)
                        .level(ObservationData.Level.ERROR)
                        .statusMessage(error.getClass().getSimpleName() + ": " + error.getMessage())
                        .metadata(meta)
                        .build());
        span.setStatus(false, error.getMessage());
        span.end();
        invokeObserver(o -> o.onHookFailed(phase, error, java.time.Duration.ofNanos(elapsedNanos)));
    }

    /**
     * Record an external hook failure event. Surfaces external command / HTTP hook failures into
     * the same observation pipeline so dashboards see in-process vs external failures uniformly.
     */
    public void recordExternalHookFailure(String phase, String hookId, Throwable error) {
        externalHookFailures.incrementAndGet();
        failuresByPhase.computeIfAbsent(phase, k -> new AtomicLong()).incrementAndGet();
        Span span = tracer.startHookSpan(null, phase, "external:" + hookId);
        Map<String, Object> meta = new HashMap<>();
        meta.put("hook.phase", phase);
        meta.put("hook.kind", "external");
        meta.put("hook.id", hookId);
        tracer.recordObservation(
                span,
                ObservationData.builder()
                        .type(ObservationData.Type.SPAN)
                        .level(ObservationData.Level.ERROR)
                        .statusMessage(error.getClass().getSimpleName() + ": " + error.getMessage())
                        .metadata(meta)
                        .build());
        span.setStatus(false, error.getMessage());
        span.end();
        invokeObserver(o -> o.onExternalHookFailure(phase, hookId, error));
    }

    /**
     * Invoke the globally registered {@link HookChainObserver}. Observer callbacks are best-effort:
     * exceptions are swallowed at {@code DEBUG} so a misbehaving metrics exporter cannot break the
     * hook chain.
     */
    private void invokeObserver(java.util.function.Consumer<HookChainObserver> call) {
        HookChainObserver observer = HookChainObserver.global();
        if (observer == null) return;
        try {
            call.accept(observer);
        } catch (Exception e) {
            log.debug("HookChainObserver callback threw: {}", e.getMessage());
        }
    }

    /**
     * Wrap a fire-method Mono to record a diagnostics event on success. Uses {@code
     * Mono.deferContextual} to access the Reactor Context and invoke the diagnostics recorder if
     * present.
     */
    @SuppressWarnings("unchecked")
    private <T> Mono<T> withDiagnosticsRecording(Mono<T> mono, String eventType) {
        return Mono.deferContextual(
                ctx ->
                        mono.doOnSuccess(
                                r -> {
                                    Object recorder =
                                            ctx.getOrDefault(DIAGNOSTICS_RECORDER_KEY, null);
                                    if (recorder instanceof Consumer<?>) {
                                        try {
                                            ((Consumer<String>) recorder).accept(eventType);
                                        } catch (Exception e) {
                                            // Best-effort — must not break the hook chain
                                            log.debug(
                                                    "Diagnostics recording failed for {}: {}",
                                                    eventType,
                                                    e.getMessage());
                                        }
                                    }
                                }));
    }
}
