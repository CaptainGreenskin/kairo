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

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.RawStreamingModelProvider;
import io.kairo.api.tracing.NoopTracer;
import io.kairo.api.tracing.ObservationData;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.tool.DefaultToolExecutor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Wraps any {@link ModelProvider} with first-class Langfuse / OTel GenAI generation spans.
 *
 * <p>For every {@code call()} / {@code stream()} invocation, opens a reasoning span via {@link
 * Tracer#startReasoningSpan(Span, String, int)} (parent pulled from {@link
 * DefaultToolExecutor#SPAN_CONTEXT_KEY} in the Reactor Context) and emits one {@link
 * ObservationData} payload at completion. Success and error paths both write the standard attribute
 * set: {@code langfuse.observation.*}, {@code gen_ai.usage.*}, {@code langfuse.usage_details}.
 *
 * <p>This is the model-citizen retrofit pattern: provider implementations stay clean (no Tracer
 * field, no SPI churn); observability is composed in at agent-build time. {@link
 * io.kairo.core.agent.AgentBuilder} auto-wraps the user-supplied provider when a non-noop tracer is
 * configured.
 *
 * <p>Use {@link #wrap(ModelProvider, Tracer)} so that {@link RawStreamingModelProvider} capability
 * of the delegate is preserved through the decorator (the wrapper instance implements {@code
 * RawStreamingModelProvider} only when the delegate does — agent code that does {@code instanceof
 * RawStreamingModelProvider} checks still works after wrapping).
 */
public class TracingModelProvider implements ModelProvider {

    protected final ModelProvider delegate;
    protected final Tracer tracer;

    /**
     * Wrap {@code delegate} so its model calls emit reasoning spans via {@code tracer}. If {@code
     * delegate} also implements {@link RawStreamingModelProvider}, the returned wrapper implements
     * it too so downstream {@code instanceof} checks keep working.
     *
     * <p>Passing a {@link NoopTracer#INSTANCE} returns the delegate unchanged — zero overhead in
     * test / headless runs.
     *
     * @param delegate the model provider to wrap (not null)
     * @param tracer the tracer to record spans with (not null)
     * @return a tracing-aware decorator, or {@code delegate} itself if {@code tracer} is a noop
     */
    public static ModelProvider wrap(ModelProvider delegate, Tracer tracer) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (tracer == null || tracer == NoopTracer.INSTANCE) {
            return delegate;
        }
        if (delegate instanceof RawStreamingModelProvider raw) {
            return new TracingRawStreamingModelProvider(raw, tracer);
        }
        return new TracingModelProvider(delegate, tracer);
    }

    TracingModelProvider(ModelProvider delegate, Tracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
        return Mono.deferContextual(
                ctxView -> {
                    Span parent =
                            ctxView.hasKey(DefaultToolExecutor.SPAN_CONTEXT_KEY)
                                    ? ctxView.get(DefaultToolExecutor.SPAN_CONTEXT_KEY)
                                    : null;
                    Span span =
                            tracer.startReasoningSpan(parent, modelName(config), messages.size());
                    long startMs = System.currentTimeMillis();
                    return delegate.call(messages, config)
                            .doOnSuccess(
                                    response ->
                                            recordSuccess(
                                                    span, messages, config, response, startMs))
                            .doOnError(err -> recordError(span, messages, config, err, startMs))
                            .doFinally(signal -> span.end());
                });
    }

    @Override
    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
        return Flux.deferContextual(
                ctxView -> {
                    Span parent =
                            ctxView.hasKey(DefaultToolExecutor.SPAN_CONTEXT_KEY)
                                    ? ctxView.get(DefaultToolExecutor.SPAN_CONTEXT_KEY)
                                    : null;
                    Span span =
                            tracer.startReasoningSpan(parent, modelName(config), messages.size());
                    span.setAttribute("gen_ai.streaming", true);
                    long startMs = System.currentTimeMillis();
                    // The final ModelResponse emitted by the SSE subscriber is the aggregated one
                    // with full usage; tap last to record it.
                    java.util.concurrent.atomic.AtomicReference<ModelResponse> last =
                            new java.util.concurrent.atomic.AtomicReference<>();
                    return delegate.stream(messages, config)
                            .doOnNext(last::set)
                            .doOnComplete(
                                    () ->
                                            recordSuccess(
                                                    span, messages, config, last.get(), startMs))
                            .doOnError(err -> recordError(span, messages, config, err, startMs))
                            .doFinally(signal -> span.end());
                });
    }

    protected void recordSuccess(
            Span span,
            List<Msg> messages,
            ModelConfig config,
            ModelResponse response,
            long startMs) {
        long latencyMs = System.currentTimeMillis() - startMs;
        Map<String, Object> meta = new HashMap<>();
        meta.put("model.latency_ms", latencyMs);
        meta.put("model.provider", delegate.name());
        if (response != null && response.stopReason() != null) {
            meta.put("gen_ai.response.finish_reason", response.stopReason().name());
        }
        if (response != null && response.id() != null) {
            meta.put("gen_ai.response.id", response.id());
        }

        ObservationData.Builder b =
                ObservationData.builder()
                        .type(ObservationData.Type.GENERATION)
                        .model(modelName(config))
                        .input(previewLastUser(messages))
                        .output(response == null ? null : previewResponse(response))
                        .level(ObservationData.Level.DEFAULT)
                        .metadata(meta);

        if (response != null && response.usage() != null) {
            int in = response.usage().inputTokens();
            int out = response.usage().outputTokens();
            b.inputTokens(in).outputTokens(out);
            // Populate cost so Langfuse's cost panel doesn't sit at $0.00. Self-hosted Langfuse
            // installs without a synced model catalog can't compute cost on their side; we ship it.
            io.kairo.core.model.ModelPricing.estimateUsd(modelName(config), in, out)
                    .ifPresent(b::costUsd);
        }
        tracer.recordObservation(span, b.build());
        span.setStatus(true, null);
    }

    protected void recordError(
            Span span, List<Msg> messages, ModelConfig config, Throwable err, long startMs) {
        long latencyMs = System.currentTimeMillis() - startMs;
        Map<String, Object> meta = new HashMap<>();
        meta.put("model.latency_ms", latencyMs);
        meta.put("model.provider", delegate.name());
        meta.put("exception.type", err.getClass().getName());

        String msg = err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage();
        ObservationData data =
                ObservationData.builder()
                        .type(ObservationData.Type.GENERATION)
                        .model(modelName(config))
                        .input(previewLastUser(messages))
                        .level(ObservationData.Level.ERROR)
                        .statusMessage(msg)
                        .metadata(meta)
                        .build();
        tracer.recordObservation(span, data);
        span.setStatus(false, msg);
    }

    private static String modelName(ModelConfig config) {
        return config == null ? null : config.model();
    }

    private static String previewLastUser(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg m = messages.get(i);
            if (m == null || m.contents() == null) continue;
            for (Content c : m.contents()) {
                if (c instanceof Content.TextContent t && t.text() != null) {
                    return clip(t.text(), 4000);
                }
            }
        }
        return null;
    }

    private static String previewResponse(ModelResponse response) {
        if (response == null || response.contents() == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Content c : response.contents()) {
            if (c instanceof Content.TextContent t && t.text() != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(t.text());
                if (sb.length() >= 4000) break;
            }
        }
        return sb.length() == 0 ? null : clip(sb.toString(), 4000);
    }

    private static String clip(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }
}
