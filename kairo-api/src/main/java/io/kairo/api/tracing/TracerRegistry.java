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

/**
 * Global registry for the {@link Tracer} instance.
 *
 * <p>This provides a global singleton tracer
 * that defaults to a no-op implementation. Register a real tracer (e.g. OpenTelemetry or
 * Micrometer-based) to enable observability.
 */
public final class TracerRegistry {

    private static volatile Tracer tracer = new NoopTracer();

    private TracerRegistry() {}

    /**
     * Register a global tracer implementation.
     *
     * @param tracer the tracer to use globally
     */
    public static void register(Tracer tracer) {
        TracerRegistry.tracer = tracer != null ? tracer : new NoopTracer();
    }

    /**
     * Get the current global tracer.
     *
     * @return the registered tracer, or a no-op tracer if none registered
     */
    public static Tracer get() {
        return tracer;
    }

    /** No-op tracer that does nothing. All default methods in Tracer are already no-ops. */
    private static final class NoopTracer implements Tracer {}
}
