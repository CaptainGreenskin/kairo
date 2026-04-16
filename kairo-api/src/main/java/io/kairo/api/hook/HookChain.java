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
package io.kairo.api.hook;

import reactor.core.publisher.Mono;

/**
 * Manages hook handler registration and event firing.
 *
 * <p>Hook handlers are POJOs with methods annotated with hook annotations (e.g. {@link
 * PreReasoning}, {@link PostActing}). The chain discovers annotated methods and invokes them in
 * order.
 */
public interface HookChain {

    /**
     * Register a hook handler object. Its annotated methods will be discovered.
     *
     * @param hookHandler the handler to register
     */
    void register(Object hookHandler);

    /**
     * Unregister a previously registered hook handler.
     *
     * @param hookHandler the handler to unregister
     */
    void unregister(Object hookHandler);

    /**
     * Fire the pre-reasoning event through all registered handlers.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the (possibly modified) event
     */
    <T> Mono<T> firePreReasoning(T event);

    /**
     * Fire the post-reasoning event through all registered handlers.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the (possibly modified) event
     */
    <T> Mono<T> firePostReasoning(T event);

    /**
     * Fire the pre-acting event through all registered handlers.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the (possibly modified) event
     */
    <T> Mono<T> firePreActing(T event);

    /**
     * Fire the post-acting event through all registered handlers.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the (possibly modified) event
     */
    <T> Mono<T> firePostActing(T event);

    /**
     * Fire the pre-compact event through all registered handlers.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the (possibly modified) event
     */
    <T> Mono<T> firePreCompact(T event);

    /**
     * Fire the post-compact event through all registered handlers.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the (possibly modified) event
     */
    <T> Mono<T> firePostCompact(T event);

    /**
     * Fire the pre-acting event and return a structured result with behavioral decisions.
     *
     * <p>Hook handlers that return {@link HookResult} can abort the operation, modify tool input,
     * or inject additional context. Handlers that return the plain event are auto-wrapped as {@link
     * HookResult#proceed(Object)}.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the structured hook result
     */
    default <T> Mono<HookResult<T>> firePreActingWithResult(T event) {
        return firePreActing(event).map(HookResult::proceed);
    }

    /**
     * Fire the post-acting event and return a structured result.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the structured hook result
     */
    default <T> Mono<HookResult<T>> firePostActingWithResult(T event) {
        return firePostActing(event).map(HookResult::proceed);
    }

    /**
     * Fire the pre-reasoning event and return a structured result with behavioral decisions.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the structured hook result
     */
    default <T> Mono<HookResult<T>> firePreReasoningWithResult(T event) {
        return firePreReasoning(event).map(HookResult::proceed);
    }

    /**
     * Fire the post-reasoning event and return a structured result.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the structured hook result
     */
    default <T> Mono<HookResult<T>> firePostReasoningWithResult(T event) {
        return firePostReasoning(event).map(HookResult::proceed);
    }

    /**
     * Fire the pre-compact event and return a structured result with behavioral decisions.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the structured hook result
     */
    default <T> Mono<HookResult<T>> firePreCompactWithResult(T event) {
        return firePreCompact(event).map(HookResult::proceed);
    }

    /**
     * Fire the post-compact event and return a structured result.
     *
     * @param event the event to fire
     * @param <T> the event type
     * @return a Mono emitting the structured hook result
     */
    default <T> Mono<HookResult<T>> firePostCompactWithResult(T event) {
        return firePostCompact(event).map(HookResult::proceed);
    }
}
