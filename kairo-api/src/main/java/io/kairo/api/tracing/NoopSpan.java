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
package io.kairo.api.tracing;

/** No-operation Span implementation. All methods are silent no-ops. */
public final class NoopSpan implements Span {
    public static final NoopSpan INSTANCE = new NoopSpan();

    private NoopSpan() {}

    @Override
    public String spanId() {
        return "";
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public Span parent() {
        return null;
    }

    @Override
    public void setAttribute(String key, Object value) {}

    @Override
    public void setStatus(boolean success, String message) {}

    @Override
    public void end() {}
}
