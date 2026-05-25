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
import io.kairo.api.tracing.ObservationData;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.tool.DefaultToolExecutor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
                    return raw.streamRaw(messages, config)
                            .doOnNext(c -> chunkCount.incrementAndGet())
                            .doOnComplete(
                                    () ->
                                            recordRawSuccess(
                                                    span,
                                                    messages,
                                                    config,
                                                    chunkCount.get(),
                                                    startMs))
                            .doOnError(err -> recordError(span, messages, config, err, startMs))
                            .doFinally(signal -> span.end());
                });
    }

    private void recordRawSuccess(
            Span span, List<Msg> messages, ModelConfig config, int chunks, long startMs) {
        long latencyMs = System.currentTimeMillis() - startMs;
        Map<String, Object> meta = new HashMap<>();
        meta.put("model.latency_ms", latencyMs);
        meta.put("model.provider", delegate.name());
        meta.put("gen_ai.streaming.chunks", (long) chunks);
        ObservationData data =
                ObservationData.builder()
                        .type(ObservationData.Type.GENERATION)
                        .model(config == null ? null : config.model())
                        .level(ObservationData.Level.DEFAULT)
                        .metadata(meta)
                        .build();
        tracer.recordObservation(span, data);
        span.setStatus(true, null);
    }
}
