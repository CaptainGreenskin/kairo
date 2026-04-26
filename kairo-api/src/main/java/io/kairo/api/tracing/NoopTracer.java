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

import io.kairo.api.Stable;

/**
 * No-operation Tracer implementation. All span factories return {@link NoopSpan#INSTANCE}. Uses
 * default method implementations from {@link Tracer}.
 */
@Stable(value = "Noop tracer default implementation; shape frozen since v0.3", since = "1.0.0")
public final class NoopTracer implements Tracer {
    public static final NoopTracer INSTANCE = new NoopTracer();
    // All methods inherited from Tracer defaults (return NoopSpan.INSTANCE)
}
