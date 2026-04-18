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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OTelSpanTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private io.opentelemetry.api.trace.Tracer otelTracer;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider =
                SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build();
        otelTracer = tracerProvider.get("test");
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    private SpanData endAndGetSpan(OTelSpan span) {
        span.end();
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertFalse(spans.isEmpty(), "Expected at least one exported span");
        return spans.get(spans.size() - 1);
    }

    // --- Attribute mapping tests ---

    @Test
    void tokenInputMapsToGenAiUsageInputTokens() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("token.input", 100L);
        SpanData data = endAndGetSpan(span);
        assertEquals(
                100L, data.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
    }

    @Test
    void tokenOutputMapsToGenAiUsageOutputTokens() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("token.output", 200L);
        SpanData data = endAndGetSpan(span);
        assertEquals(
                200L, data.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));
    }

    @Test
    void tokenCacheReadMapsToGenAiUsageCacheReadTokens() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("token.cache_read", 50L);
        SpanData data = endAndGetSpan(span);
        assertEquals(
                50L,
                data.getAttributes().get(AttributeKey.longKey("gen_ai.usage.cache_read_tokens")));
    }

    @Test
    void tokenCacheWriteMapsToGenAiUsageCacheCreationTokens() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("token.cache_write", 30L);
        SpanData data = endAndGetSpan(span);
        assertEquals(
                30L,
                data.getAttributes()
                        .get(AttributeKey.longKey("gen_ai.usage.cache_creation_tokens")));
    }

    @Test
    void toolNameMapsToGenAiToolName() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("tool.name", "calculator");
        SpanData data = endAndGetSpan(span);
        assertEquals(
                "calculator", data.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.name")));
    }

    @Test
    void toolSuccessMapsToGenAiToolSuccess() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("tool.success", true);
        SpanData data = endAndGetSpan(span);
        assertEquals(
                true, data.getAttributes().get(AttributeKey.booleanKey("gen_ai.tool.success")));
    }

    @Test
    void toolDurationMsMapsToGenAiToolDurationMs() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("tool.duration_ms", 1500L);
        SpanData data = endAndGetSpan(span);
        assertEquals(
                1500L, data.getAttributes().get(AttributeKey.longKey("gen_ai.tool.duration_ms")));
    }

    @Test
    void exceptionTypeMapsCorrectly() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("exception.type", "java.lang.RuntimeException");
        SpanData data = endAndGetSpan(span);
        assertEquals(
                "java.lang.RuntimeException",
                data.getAttributes().get(AttributeKey.stringKey("exception.type")));
    }

    @Test
    void exceptionMessageMapsCorrectly() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("exception.message", "something went wrong");
        SpanData data = endAndGetSpan(span);
        assertEquals(
                "something went wrong",
                data.getAttributes().get(AttributeKey.stringKey("exception.message")));
    }

    @Test
    void compactionStrategyMapsCorrectly() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("compaction.strategy", "summarize");
        SpanData data = endAndGetSpan(span);
        assertEquals(
                "summarize",
                data.getAttributes().get(AttributeKey.stringKey("gen_ai.compaction.strategy")));
    }

    @Test
    void compactionTokensSavedMapsCorrectly() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("compaction.tokens_saved", 5000L);
        SpanData data = endAndGetSpan(span);
        assertEquals(
                5000L,
                data.getAttributes().get(AttributeKey.longKey("gen_ai.compaction.tokens_saved")));
    }

    // --- Unmapped key pass-through ---

    @Test
    void unmappedKeyPassesThroughAsIs() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("my.custom.key", "custom-value");
        SpanData data = endAndGetSpan(span);
        assertEquals(
                "custom-value", data.getAttributes().get(AttributeKey.stringKey("my.custom.key")));
    }

    // --- Type handling ---

    @Test
    void longValueSetCorrectly() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("my.long", 42L);
        SpanData data = endAndGetSpan(span);
        assertEquals(42L, data.getAttributes().get(AttributeKey.longKey("my.long")));
    }

    @Test
    void integerValueConvertedToLong() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("my.int", 42);
        SpanData data = endAndGetSpan(span);
        assertEquals(42L, data.getAttributes().get(AttributeKey.longKey("my.int")));
    }

    @Test
    void doubleValueSetCorrectly() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("my.double", 3.14);
        SpanData data = endAndGetSpan(span);
        assertEquals(3.14, data.getAttributes().get(AttributeKey.doubleKey("my.double")));
    }

    @Test
    void booleanValueSetCorrectly() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("my.bool", true);
        SpanData data = endAndGetSpan(span);
        assertEquals(true, data.getAttributes().get(AttributeKey.booleanKey("my.bool")));
    }

    @Test
    void stringValueSetCorrectly() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("my.string", "hello");
        SpanData data = endAndGetSpan(span);
        assertEquals("hello", data.getAttributes().get(AttributeKey.stringKey("my.string")));
    }

    @Test
    void nullKeyIsIgnored() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute(null, "value");
        SpanData data = endAndGetSpan(span);
        assertTrue(data.getAttributes().isEmpty());
    }

    @Test
    void nullValueIsIgnored() {
        OTelSpan span = createSpan("test-span");
        span.setAttribute("key", null);
        SpanData data = endAndGetSpan(span);
        assertTrue(data.getAttributes().isEmpty());
    }

    // --- setStatus ---

    @Test
    void setStatusSuccessTrue() {
        OTelSpan span = createSpan("test-span");
        span.setStatus(true, "all good");
        SpanData data = endAndGetSpan(span);
        assertEquals(StatusCode.OK, data.getStatus().getStatusCode());
        assertEquals("all good", data.getStatus().getDescription());
    }

    @Test
    void setStatusSuccessFalse() {
        OTelSpan span = createSpan("test-span");
        span.setStatus(false, "something failed");
        SpanData data = endAndGetSpan(span);
        assertEquals(StatusCode.ERROR, data.getStatus().getStatusCode());
        assertEquals("something failed", data.getStatus().getDescription());
    }

    @Test
    void setStatusWithNullMessage() {
        OTelSpan span = createSpan("test-span");
        span.setStatus(true, null);
        SpanData data = endAndGetSpan(span);
        assertEquals(StatusCode.OK, data.getStatus().getStatusCode());
        assertEquals("", data.getStatus().getDescription());
    }

    // --- Accessors ---

    @Test
    void spanIdReturnsNonEmptyString() {
        OTelSpan span = createSpan("test-span");
        assertNotNull(span.spanId());
        assertFalse(span.spanId().isEmpty());
        span.end();
    }

    @Test
    void nameReturnsConfiguredName() {
        OTelSpan span = createSpan("my-span-name");
        assertEquals("my-span-name", span.name());
        span.end();
    }

    @Test
    void parentReturnsNullForRootSpan() {
        OTelSpan span = createSpan("root");
        assertNull(span.parent());
        span.end();
    }

    @Test
    void parentReturnsParentSpan() {
        OTelSpan parent = createSpan("parent");
        io.opentelemetry.api.trace.Span childOtel = otelTracer.spanBuilder("child").startSpan();
        OTelSpan child = new OTelSpan(childOtel, "child", parent);
        assertEquals(parent, child.parent());
        child.end();
        parent.end();
    }

    @Test
    void endExportsSpan() {
        OTelSpan span = createSpan("test-span");
        assertTrue(exporter.getFinishedSpanItems().isEmpty());
        span.end();
        assertEquals(1, exporter.getFinishedSpanItems().size());
        assertEquals("test-span", exporter.getFinishedSpanItems().get(0).getName());
    }

    // --- addEvent ---

    @Test
    void addEventWithoutAttributes() {
        OTelSpan span = createSpan("test-span");
        span.addEvent("model.request");
        SpanData data = endAndGetSpan(span);
        List<EventData> events = data.getEvents();
        assertEquals(1, events.size());
        assertEquals("model.request", events.get(0).getName());
        assertTrue(events.get(0).getAttributes().isEmpty());
    }

    @Test
    void addEventWithStringAttribute() {
        OTelSpan span = createSpan("test-span");
        span.addEvent("tool.result", Map.of("output", "success"));
        SpanData data = endAndGetSpan(span);
        List<EventData> events = data.getEvents();
        assertEquals(1, events.size());
        assertEquals("tool.result", events.get(0).getName());
        assertEquals(
                "success", events.get(0).getAttributes().get(AttributeKey.stringKey("output")));
    }

    @Test
    void addEventWithVariousValueTypes() {
        OTelSpan span = createSpan("test-span");
        span.addEvent("multi.type", Map.of("str", "hello", "lng", 42L, "dbl", 3.14, "bool", true));
        SpanData data = endAndGetSpan(span);
        EventData event = data.getEvents().get(0);
        assertEquals("hello", event.getAttributes().get(AttributeKey.stringKey("str")));
        assertEquals(42L, event.getAttributes().get(AttributeKey.longKey("lng")));
        assertEquals(3.14, event.getAttributes().get(AttributeKey.doubleKey("dbl")));
        assertEquals(true, event.getAttributes().get(AttributeKey.booleanKey("bool")));
    }

    @Test
    void addEventWithIntegerConvertsToLong() {
        OTelSpan span = createSpan("test-span");
        span.addEvent("int.event", Map.of("count", 7));
        SpanData data = endAndGetSpan(span);
        EventData event = data.getEvents().get(0);
        assertEquals(7L, event.getAttributes().get(AttributeKey.longKey("count")));
    }

    @Test
    void addEventWithNullAttributes() {
        OTelSpan span = createSpan("test-span");
        span.addEvent("null.attrs", null);
        SpanData data = endAndGetSpan(span);
        assertEquals(1, data.getEvents().size());
        assertEquals("null.attrs", data.getEvents().get(0).getName());
    }

    @Test
    void addEventWithEmptyAttributes() {
        OTelSpan span = createSpan("test-span");
        span.addEvent("empty.attrs", Map.of());
        SpanData data = endAndGetSpan(span);
        assertEquals(1, data.getEvents().size());
        assertEquals("empty.attrs", data.getEvents().get(0).getName());
        assertTrue(data.getEvents().get(0).getAttributes().isEmpty());
    }

    @Test
    void addMultipleEvents() {
        OTelSpan span = createSpan("test-span");
        span.addEvent("event1");
        span.addEvent("event2", Map.of("key", "val"));
        span.addEvent("event3");
        SpanData data = endAndGetSpan(span);
        assertEquals(3, data.getEvents().size());
        assertEquals("event1", data.getEvents().get(0).getName());
        assertEquals("event2", data.getEvents().get(1).getName());
        assertEquals("event3", data.getEvents().get(2).getName());
    }

    @Test
    void addEventWithNonStandardObjectUsesToString() {
        OTelSpan span = createSpan("test-span");
        span.addEvent("obj.event", Map.of("list", List.of(1, 2, 3)));
        SpanData data = endAndGetSpan(span);
        EventData event = data.getEvents().get(0);
        assertEquals("[1, 2, 3]", event.getAttributes().get(AttributeKey.stringKey("list")));
    }

    private OTelSpan createSpan(String name) {
        io.opentelemetry.api.trace.Span otelSpan = otelTracer.spanBuilder(name).startSpan();
        return new OTelSpan(otelSpan, name, null);
    }
}
