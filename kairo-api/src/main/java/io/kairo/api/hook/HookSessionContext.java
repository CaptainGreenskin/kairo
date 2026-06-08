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

import io.kairo.api.Experimental;

/**
 * Per-session state context for hook handlers.
 *
 * <p>Provides a key-value store scoped to a single agent session. Hook handlers that accept a
 * {@code HookSessionContext} as their second parameter receive the session's context automatically
 * from {@link HookChain} during dispatch.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @HookHandler(HookPhase.POST_REASONING)
 * HookResult<PostReasoningEvent> onPostReasoning(PostReasoningEvent event, HookSessionContext ctx) {
 *     int count = ctx.incrementCounter("post_reasoning_calls");
 *     if (count > 10) { ... }
 *     return HookResult.proceed(event);
 * }
 * }</pre>
 *
 * <p>Implementations must be thread-safe — hooks may fire concurrently during parallel tool
 * execution.
 *
 * @see NoopHookSessionContext
 */
@Experimental("HookSessionContext SPI v0.11")
public interface HookSessionContext {

    /**
     * The session identifier this context belongs to.
     *
     * @return the session/agent ID, never {@code null}
     */
    String sessionId();

    /**
     * Retrieve a typed value from the session state.
     *
     * @param key the state key
     * @param type the expected value type
     * @param <T> the value type
     * @return the value, or {@code null} if not set or type mismatch
     */
    <T> T get(String key, Class<T> type);

    /**
     * Store a value in the session state.
     *
     * @param key the state key
     * @param value the value to store (null removes the key)
     */
    void set(String key, Object value);

    /**
     * Atomically increment a named counter and return the new value.
     *
     * @param key the counter key
     * @return the value after incrementing (starts at 1 on first call)
     */
    int incrementCounter(String key);

    /**
     * Read the current value of a named counter without modifying it.
     *
     * @param key the counter key
     * @return the current count, or 0 if the counter has not been incremented
     */
    int getCounter(String key);
}
