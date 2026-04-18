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
package io.kairo.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tracing.NoopTracer;
import io.kairo.api.tracing.Tracer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OTelTracerFactoryTest {

    @AfterEach
    void tearDown() {
        // Reset GlobalOpenTelemetry after each test
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void createWithOpenTelemetryReturnsOTelTracer() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tp =
                SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder().setTracerProvider(tp).build();

        Tracer tracer = OTelTracerFactory.create(otel);

        assertNotNull(tracer);
        assertInstanceOf(OTelTracer.class, tracer);
        tp.close();
    }

    @Test
    void createWithNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> OTelTracerFactory.create(null));
    }

    @Test
    void createWithGlobalOpenTelemetryReturnsOTelTracer() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tp =
                SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build();
        OpenTelemetrySdk otel =
                OpenTelemetrySdk.builder().setTracerProvider(tp).buildAndRegisterGlobal();

        Tracer tracer = OTelTracerFactory.create();

        assertNotNull(tracer);
        assertInstanceOf(OTelTracer.class, tracer);
        tp.close();
    }

    @Test
    void createWithNoGlobalConfigReturnsTracer() {
        // When GlobalOpenTelemetry is not configured, create() should still return
        // a valid Tracer (either OTelTracer wrapping noop or NoopTracer)
        Tracer tracer = OTelTracerFactory.create();
        assertNotNull(tracer);
        // The factory returns OTelTracer wrapping noop OTel, or NoopTracer on error
        assertTrue(tracer instanceof OTelTracer || tracer instanceof NoopTracer);
    }

    @Test
    void createdTracerProducesValidSpans() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tp =
                SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build();
        OpenTelemetrySdk otel = OpenTelemetrySdk.builder().setTracerProvider(tp).build();

        Tracer tracer = OTelTracerFactory.create(otel);
        var span =
                tracer.startAgentSpan(
                        "test-agent",
                        io.kairo.api.message.Msg.of(io.kairo.api.message.MsgRole.USER, "hello"));
        span.end();

        assertEquals(1, exporter.getFinishedSpanItems().size());
        assertEquals("agent:test-agent", exporter.getFinishedSpanItems().get(0).getName());
        tp.close();
    }
}
