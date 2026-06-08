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

import io.kairo.api.hook.HookSessionContext;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default in-memory {@link HookSessionContext} backed by concurrent maps.
 *
 * <p>Thread-safe for concurrent hook access during parallel tool execution.
 */
public final class DefaultHookSessionContext implements HookSessionContext {

    private final String sessionId;
    private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public DefaultHookSessionContext(String sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = state.get(key);
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        return null;
    }

    @Override
    public void set(String key, Object value) {
        if (value == null) {
            state.remove(key);
        } else {
            state.put(key, value);
        }
    }

    @Override
    public int incrementCounter(String key) {
        return counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public int getCounter(String key) {
        AtomicInteger counter = counters.get(key);
        return counter != null ? counter.get() : 0;
    }
}
