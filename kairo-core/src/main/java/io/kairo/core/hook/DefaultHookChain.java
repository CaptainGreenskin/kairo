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
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private final List<Object> handlers = new CopyOnWriteArrayList<>();
    private final List<ExternalHookExecutor> externalExecutors = new CopyOnWriteArrayList<>();
    private final List<ExternalHookBinding> externalBindings = new CopyOnWriteArrayList<>();

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

        T current = event;
        for (AnnotatedMethod am : methods) {
            if (isCancelled(current)) break;
            try {
                Object result = am.method().invoke(am.handler(), current);
                if (result != null && event.getClass().isInstance(result)) {
                    current = (T) result;
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                log.error(
                        "Hook method {}#{} threw exception",
                        am.handler().getClass().getSimpleName(),
                        am.method().getName(),
                        cause);
                return Mono.error(cause != null ? cause : e);
            } catch (IllegalAccessException e) {
                log.error(
                        "Hook method {}#{} is not accessible",
                        am.handler().getClass().getSimpleName(),
                        am.method().getName(),
                        e);
                return Mono.error(e);
            }
        }
        return Mono.just(current);
    }

    @SuppressWarnings("unchecked")
    private <T extends HookEvent> Mono<HookResult<T>> firePhaseInProcessWithResult(
            HookPhase phase, T event) {
        List<AnnotatedMethod> methods = findMethodsForPhase(phase);
        if (methods.isEmpty()) {
            return Mono.just(HookResult.proceed(event));
        }

        T current = event;
        HookResult.Decision winningDecision = HookResult.Decision.CONTINUE;
        String winningReason = null;

        for (AnnotatedMethod am : methods) {
            if (isCancelled(current)) break;
            try {
                Object result = am.method().invoke(am.handler(), current);
                if (result instanceof HookResult<?> hr) {
                    HookResult<T> typed = (HookResult<T>) hr;
                    current = typed.event();
                    if (typed.decision() == HookResult.Decision.ABORT) {
                        return Mono.just(typed);
                    }
                    if (typed.decision().priority() > winningDecision.priority()) {
                        winningDecision = typed.decision();
                        if (typed.reason() != null) winningReason = typed.reason();
                    }
                } else if (result != null && event.getClass().isInstance(result)) {
                    current = (T) result;
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                log.error("Hook threw exception", cause);
                return Mono.error(cause != null ? cause : e);
            } catch (IllegalAccessException e) {
                return Mono.error(e);
            }
        }

        return Mono.just(
                new HookResult<>(current, winningDecision, null, null, winningReason, null, null));
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
                                                log.warn(
                                                        "External hook failed: {}", e.getMessage());
                                                return Mono.just(HookResult.proceed(event));
                                            });
                        })
                .reduce(baseline, (acc, result) -> mergeResults(acc, (HookResult<T>) result));
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
                if (method.getParameterCount() != 1) continue;
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
        return Mono.defer(
                () -> {
                    List<AnnotatedMethod> methods = findAnnotatedMethods(annotationType);
                    if (methods.isEmpty()) {
                        return Mono.just(event);
                    }

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

                        try {
                            Object result = am.method().invoke(am.handler(), current);
                            if (result != null && event.getClass().isInstance(result)) {
                                current = (T) result;
                            }
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            log.error(
                                    "Hook method {}#{} threw exception",
                                    am.handler().getClass().getSimpleName(),
                                    am.method().getName(),
                                    cause);
                            return Mono.error(cause != null ? cause : e);
                        } catch (IllegalAccessException e) {
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
        return Mono.defer(
                () -> {
                    List<AnnotatedMethod> methods = findAnnotatedMethods(annotationType);
                    if (methods.isEmpty()) {
                        return Mono.just(HookResult.proceed(event));
                    }

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

                        try {
                            Object result = am.method().invoke(am.handler(), current);

                            if (result instanceof HookResult<?> hr) {
                                HookResult<T> typed = (HookResult<T>) hr;
                                current = typed.event();

                                // ABORT always short-circuits
                                if (typed.decision() == HookResult.Decision.ABORT) {
                                    log.debug(
                                            "Hook {}#{} aborted: {}",
                                            am.handler().getClass().getSimpleName(),
                                            am.method().getName(),
                                            typed.reason());
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
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            log.error(
                                    "Hook method {}#{} threw exception",
                                    am.handler().getClass().getSimpleName(),
                                    am.method().getName(),
                                    cause);
                            return Mono.error(cause != null ? cause : e);
                        } catch (IllegalAccessException e) {
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
                if (method.getParameterCount() != 1) {
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
