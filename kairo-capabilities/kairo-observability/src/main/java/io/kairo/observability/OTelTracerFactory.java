/*
 * Copyright 2025 Kairo Team
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
package io.kairo.observability;

import io.kairo.api.tracing.NoopTracer;
import io.kairo.api.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Factory for creating {@link OTelTracer} instances with auto-detection and graceful fallback.
 *
 * <p>Two creation strategies are supported:
 *
 * <ul>
 *   <li>{@link #create()} — auto-detects the OTel SDK via {@link
 *       io.opentelemetry.api.GlobalOpenTelemetry#get() GlobalOpenTelemetry.get()}. If the SDK is
 *       not on the classpath or not configured, falls back to {@link NoopTracer#INSTANCE}.
 *   <li>{@link #create(OpenTelemetry)} — uses a caller-provided {@link OpenTelemetry} instance,
 *       giving full control over SDK configuration.
 * </ul>
 *
 * <p><strong>Usage example (auto-detect):</strong>
 *
 * <pre>{@code
 * Tracer tracer = OTelTracerFactory.create();
 * }</pre>
 *
 * <p><strong>Usage example (user-provided SDK):</strong>
 *
 * <pre>{@code
 * SdkTracerProvider tp = SdkTracerProvider.builder()
 *     .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
 *     .build();
 * OpenTelemetry otel = OpenTelemetrySdk.builder()
 *     .setTracerProvider(tp)
 *     .build();
 * Tracer tracer = OTelTracerFactory.create(otel);
 * }</pre>
 *
 * @see OTelTracer
 * @see NoopTracer
 */
public final class OTelTracerFactory {

    private OTelTracerFactory() {}

    /**
     * Create an {@link OTelTracer} using the {@link io.opentelemetry.api.GlobalOpenTelemetry}
     * instance.
     *
     * <p>If the OTel SDK is not on the classpath or {@code GlobalOpenTelemetry} has not been
     * configured, this method gracefully falls back to {@link NoopTracer#INSTANCE} rather than
     * throwing an exception.
     *
     * @return a {@link Tracer} backed by OTel, or {@link NoopTracer#INSTANCE} on failure
     */
    public static Tracer create() {
        try {
            OpenTelemetry otel = io.opentelemetry.api.GlobalOpenTelemetry.get();
            return create(otel);
        } catch (Throwable e) {
            // OTel SDK not on classpath or not configured — fall back silently
            return NoopTracer.INSTANCE;
        }
    }

    /**
     * Create an {@link OTelTracer} with a caller-provided {@link OpenTelemetry} instance.
     *
     * @param openTelemetry the configured OTel instance, must not be {@code null}
     * @return a new {@link OTelTracer} backed by the given OTel instance
     * @throws NullPointerException if {@code openTelemetry} is {@code null}
     */
    public static Tracer create(OpenTelemetry openTelemetry) {
        if (openTelemetry == null) {
            throw new NullPointerException("openTelemetry must not be null");
        }
        io.opentelemetry.api.trace.Tracer otelTracer =
                openTelemetry.getTracer("io.kairo", KairoObservability.MODULE_VERSION);
        return new OTelTracer(otelTracer);
    }
}
