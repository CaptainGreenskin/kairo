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
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final List<Object> handlers = new CopyOnWriteArrayList<>();

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

    @Override
    public <T> Mono<T> firePreReasoning(T event) {
        return fireEvent(event, PreReasoning.class);
    }

    @Override
    public <T> Mono<T> firePostReasoning(T event) {
        return fireEvent(event, PostReasoning.class);
    }

    @Override
    public <T> Mono<T> firePreActing(T event) {
        return fireEvent(event, PreActing.class);
    }

    @Override
    public <T> Mono<T> firePostActing(T event) {
        return fireEvent(event, PostActing.class);
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
     * Fire an event and collect structured results. If a hook method returns a {@link HookResult},
     * its decision is respected. If it returns the plain event, it is auto-wrapped as {@link
     * HookResult#proceed(Object)}. An ABORT decision short-circuits the chain.
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
                    HookResult<T> accumulated = HookResult.proceed(event);

                    for (AnnotatedMethod am : methods) {
                        if (isCancelled(current)) {
                            break;
                        }

                        try {
                            Object result = am.method().invoke(am.handler(), current);

                            if (result instanceof HookResult<?> hr) {
                                // Hook returned a structured result
                                accumulated = (HookResult<T>) hr;
                                current = accumulated.event();

                                if (!accumulated.shouldProceed()) {
                                    log.debug(
                                            "Hook {}#{} aborted: {}",
                                            am.handler().getClass().getSimpleName(),
                                            am.method().getName(),
                                            accumulated.reason());
                                    break;
                                }
                            } else if (result != null && event.getClass().isInstance(result)) {
                                // Hook returned a plain event — auto-wrap
                                current = (T) result;
                                accumulated = HookResult.proceed(current);
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
                    return Mono.just(accumulated);
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
     * Find all methods annotated with the given annotation across all registered handlers, sorted
     * by their order value.
     */
    private List<AnnotatedMethod> findAnnotatedMethods(Class<? extends Annotation> annotationType) {
        List<AnnotatedMethod> result = new ArrayList<>();

        for (Object handler : handlers) {
            for (Method method : handler.getClass().getMethods()) {
                Annotation annotation = method.getAnnotation(annotationType);
                if (annotation != null && method.getParameterCount() == 1) {
                    method.setAccessible(true);
                    int order = getOrder(annotation);
                    result.add(new AnnotatedMethod(handler, method, order));
                }
            }
        }

        result.sort(Comparator.comparingInt(AnnotatedMethod::order));
        return result;
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
}
