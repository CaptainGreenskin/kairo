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
package io.kairo.core.tracing;

import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.RawStreamingModelProvider;
import io.kairo.api.model.StreamChunk;
import io.kairo.api.model.StreamChunkType;
import io.kairo.api.tracing.ObservationData;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.model.ModelPricing;
import io.kairo.core.tool.DefaultToolExecutor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;

/**
 * Tracing decorator variant that preserves the {@link RawStreamingModelProvider} marker so
 * downstream {@code instanceof} checks in {@code ReasoningPhase} still see the streaming capability
 * after wrapping.
 *
 * <p>Wraps {@link #streamRaw(List, ModelConfig)} in a reasoning span and records a final
 * observation when the chunk stream completes (we do not have per-chunk usage; raw streaming
 * reports the count of emitted chunks instead via {@code gen_ai.streaming.chunks} metadata).
 */
public final class TracingRawStreamingModelProvider extends TracingModelProvider
        implements RawStreamingModelProvider {

    TracingRawStreamingModelProvider(RawStreamingModelProvider delegate, Tracer tracer) {
        super(delegate, tracer);
    }

    @Override
    public Flux<StreamChunk> streamRaw(List<Msg> messages, ModelConfig config) {
        RawStreamingModelProvider raw = (RawStreamingModelProvider) delegate;
        return Flux.deferContextual(
                ctxView -> {
                    Span parent =
                            ctxView.hasKey(DefaultToolExecutor.SPAN_CONTEXT_KEY)
                                    ? ctxView.get(DefaultToolExecutor.SPAN_CONTEXT_KEY)
                                    : null;
                    Span span =
                            tracer.startReasoningSpan(
                                    parent,
                                    config == null ? null : config.model(),
                                    messages == null ? 0 : messages.size());
                    span.setAttribute("gen_ai.streaming", true);
                    span.setAttribute("gen_ai.streaming.raw", true);
                    long startMs = System.currentTimeMillis();
                    AtomicInteger chunkCount = new AtomicInteger();
                    // Captures the authoritative usage frame emitted by RawOpenAISseSubscriber
                    // when stream_options.include_usage=true. Null means the provider didn't
                    // surface usage out-of-band (legacy path) — recordRawSuccess falls back to
                    // zero so Langfuse still gets a generation, just without cost.
                    AtomicReference<int[]> usageRef = new AtomicReference<>();
                    return raw.streamRaw(messages, config)
                            .doOnNext(
                                    chunk -> {
                                        chunkCount.incrementAndGet();
                                        if (chunk.type() == StreamChunkType.USAGE
                                                && chunk.metadata() != null) {
                                            Object in =
                                                    chunk.metadata()
                                                            .get("gen_ai.usage.input_tokens");
                                            Object out =
                                                    chunk.metadata()
                                                            .get("gen_ai.usage.output_tokens");
                                            if (in instanceof Number n1
                                                    && out instanceof Number n2) {
                                                usageRef.set(
                                                        new int[] {n1.intValue(), n2.intValue()});
                                            }
                                        }
                                    })
                            .doOnComplete(
                                    () ->
                                            recordRawSuccess(
                                                    span,
                                                    messages,
                                                    config,
                                                    chunkCount.get(),
                                                    usageRef.get(),
                                                    startMs))
                            .doOnError(err -> recordError(span, messages, config, err, startMs))
                            .doFinally(signal -> span.end());
                });
    }

    private void recordRawSuccess(
            Span span,
            List<Msg> messages,
            ModelConfig config,
            int chunks,
            int[] usage,
            long startMs) {
        long latencyMs = System.currentTimeMillis() - startMs;
        Map<String, Object> meta = new HashMap<>();
        meta.put("model.latency_ms", latencyMs);
        meta.put("model.provider", delegate.name());
        meta.put("gen_ai.streaming.chunks", (long) chunks);
        ObservationData.Builder b =
                ObservationData.builder()
                        .type(ObservationData.Type.GENERATION)
                        .model(config == null ? null : config.model())
                        .level(ObservationData.Level.DEFAULT)
                        .metadata(meta);
        if (usage != null) {
            int in = usage[0];
            int out = usage[1];
            b.inputTokens(in).outputTokens(out);
            // Cost only when both usage AND a price entry exist — guessing either silently is
            // worse than $0 in the dashboard (which at least signals "model not in catalog").
            ModelPricing.estimateUsd(config == null ? null : config.model(), in, out)
                    .ifPresent(b::costUsd);
        }
        tracer.recordObservation(span, b.build());
        span.setStatus(true, null);
    }
}
