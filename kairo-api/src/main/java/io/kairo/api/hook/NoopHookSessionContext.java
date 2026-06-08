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

/**
 * No-op {@link HookSessionContext} that discards all state operations.
 *
 * <p>Used as the default when no session context is configured.
 */
public final class NoopHookSessionContext implements HookSessionContext {

    public static final NoopHookSessionContext INSTANCE = new NoopHookSessionContext();

    private NoopHookSessionContext() {}

    @Override
    public String sessionId() {
        return "";
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        return null;
    }

    @Override
    public void set(String key, Object value) {}

    @Override
    public int incrementCounter(String key) {
        return 0;
    }

    @Override
    public int getCounter(String key) {
        return 0;
    }
}
