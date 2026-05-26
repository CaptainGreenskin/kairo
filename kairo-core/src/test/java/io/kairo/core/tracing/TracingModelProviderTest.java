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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.RawStreamingModelProvider;
import io.kairo.api.model.StreamChunk;
import io.kairo.api.tracing.NoopSpan;
import io.kairo.api.tracing.NoopTracer;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class TracingModelProviderTest {

    @Test
    void wrap_returnsDelegateUnchangedForNoopTracer() {
        ModelProvider raw = new FakeProvider(null, null);
        ModelProvider wrapped = TracingModelProvider.wrap(raw, NoopTracer.INSTANCE);
        assertThat(wrapped).isSameAs(raw);
    }

    @Test
    void wrap_preservesRawStreamingCapability() {
        FakeRawProvider raw = new FakeRawProvider();
        ModelProvider wrapped = TracingModelProvider.wrap(raw, new RecordingTracer());
        assertThat(wrapped)
                .isInstanceOf(RawStreamingModelProvider.class)
                .isInstanceOf(TracingRawStreamingModelProvider.class);
    }

    @Test
    void call_success_emitsLangfuseObservationAttributes() {
        ModelResponse response =
                new ModelResponse(
                        "msg-123",
                        List.of(new Content.TextContent("Hello, world!")),
                        new ModelResponse.Usage(42, 7, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");
        RecordingTracer tracer = new RecordingTracer();
        ModelProvider wrapped = TracingModelProvider.wrap(new FakeProvider(response, null), tracer);

        ModelResponse out =
                wrapped.call(
                                List.of(Msg.of(MsgRole.USER, "How are you?")),
                                ModelConfig.builder().model("test-model").build())
                        .block();

        assertThat(out).isSameAs(response);
        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.ended).isTrue();
        assertThat(span.statusSuccess).isTrue();
        assertThat(span.attributes)
                .containsEntry("langfuse.observation.type", "generation")
                .containsEntry("langfuse.observation.level", "DEFAULT")
                .containsEntry("langfuse.observation.model", "test-model")
                .containsEntry("langfuse.observation.input", "How are you?")
                .containsEntry("langfuse.observation.output", "Hello, world!")
                .containsEntry("gen_ai.request.model", "test-model")
                .containsEntry("gen_ai.usage.input_tokens", 42)
                .containsEntry("gen_ai.usage.output_tokens", 7)
                .containsEntry("model.provider", "fake")
                .containsEntry("gen_ai.response.finish_reason", "END_TURN")
                .containsEntry("gen_ai.response.id", "msg-123")
                .containsKey("langfuse.usage_details")
                .containsKey("model.latency_ms");

        @SuppressWarnings("unchecked")
        Map<String, Integer> usage =
                (Map<String, Integer>) span.attributes.get("langfuse.usage_details");
        assertThat(usage).containsEntry("input", 42).containsEntry("output", 7);
    }

    @Test
    void call_error_setsLevelErrorAndStatusMessage() {
        RuntimeException boom = new RuntimeException("api down");
        RecordingTracer tracer = new RecordingTracer();
        ModelProvider wrapped = TracingModelProvider.wrap(new FakeProvider(null, boom), tracer);

        Throwable caught = null;
        try {
            wrapped.call(
                            List.of(Msg.of(MsgRole.USER, "ping")),
                            ModelConfig.builder().model("test-model").build())
                    .block();
        } catch (Throwable t) {
            caught = t;
        }
        assertThat(caught).isNotNull();

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.ended).isTrue();
        assertThat(span.statusSuccess).isFalse();
        assertThat(span.statusMessage).isEqualTo("api down");
        assertThat(span.attributes)
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("langfuse.observation.status_message", "api down")
                .containsEntry("exception.type", RuntimeException.class.getName());
    }

    @Test
    void streamRaw_recordsChunkCountOnComplete() {
        FakeRawProvider raw = new FakeRawProvider();
        RecordingTracer tracer = new RecordingTracer();
        RawStreamingModelProvider wrapped =
                (RawStreamingModelProvider) TracingModelProvider.wrap(raw, tracer);

        List<StreamChunk> chunks =
                wrapped.streamRaw(
                                List.of(Msg.of(MsgRole.USER, "stream me")),
                                ModelConfig.builder().model("test-model").build())
                        .collectList()
                        .block();

        assertThat(chunks).hasSize(3);
        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.ended).isTrue();
        assertThat(span.statusSuccess).isTrue();
        assertThat(span.attributes)
                .containsEntry("langfuse.observation.type", "generation")
                .containsEntry("gen_ai.streaming", true)
                .containsEntry("gen_ai.streaming.raw", true)
                .containsEntry("gen_ai.streaming.chunks", 3L)
                // Langfuse Input panel needs the prompt; the raw-streaming path used to skip this,
                // which was the visible "Input: null" bug in the Langfuse UI for every reasoning
                // span.
                .containsEntry("langfuse.observation.input", "stream me")
                // Output is the accumulated TEXT chunks ("a" + "b" + "c") — without this Langfuse's
                // Output panel showed null even though the model produced content.
                .containsEntry("langfuse.observation.output", "abc");
    }

    @Test
    void streamRaw_usageChunk_populatesTokensAndCost() {
        FakeRawProviderWithUsage raw = new FakeRawProviderWithUsage(123, 45);
        RecordingTracer tracer = new RecordingTracer();
        RawStreamingModelProvider wrapped =
                (RawStreamingModelProvider) TracingModelProvider.wrap(raw, tracer);

        // gpt-4o has a price entry ($2.50 / $10 per 1M), so cost_details must be set
        wrapped.streamRaw(
                        List.of(Msg.of(MsgRole.USER, "stream me")),
                        ModelConfig.builder().model("gpt-4o").build())
                .collectList()
                .block();

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.ended).isTrue();
        assertThat(span.statusSuccess).isTrue();
        assertThat(span.attributes)
                .containsEntry("gen_ai.usage.input_tokens", 123)
                .containsEntry("gen_ai.usage.output_tokens", 45)
                .containsKey("langfuse.usage_details")
                .containsKey("langfuse.cost_details");

        @SuppressWarnings("unchecked")
        Map<String, Integer> usage =
                (Map<String, Integer>) span.attributes.get("langfuse.usage_details");
        assertThat(usage).containsEntry("input", 123).containsEntry("output", 45);

        @SuppressWarnings("unchecked")
        Map<String, Double> cost =
                (Map<String, Double>) span.attributes.get("langfuse.cost_details");
        assertThat(cost).containsKey("total");
        assertThat(cost.get("total")).isGreaterThan(0.0);
    }

    // --- Fakes ---

    private static class FakeProvider implements ModelProvider {
        private final ModelResponse response;
        private final RuntimeException error;

        FakeProvider(ModelResponse response, RuntimeException error) {
            this.response = response;
            this.error = error;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
            if (error != null) return Mono.error(error);
            return Mono.just(response);
        }

        @Override
        public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
            if (error != null) return Flux.error(error);
            return Flux.just(response);
        }
    }

    private static final class FakeRawProvider extends FakeProvider
            implements RawStreamingModelProvider {

        FakeRawProvider() {
            super(null, null);
        }

        @Override
        public Flux<StreamChunk> streamRaw(List<Msg> messages, ModelConfig config) {
            return Flux.just(StreamChunk.text("a"), StreamChunk.text("b"), StreamChunk.text("c"));
        }
    }

    private static final class FakeRawProviderWithUsage extends FakeProvider
            implements RawStreamingModelProvider {
        private final int inputTokens;
        private final int outputTokens;

        FakeRawProviderWithUsage(int inputTokens, int outputTokens) {
            super(null, null);
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }

        @Override
        public Flux<StreamChunk> streamRaw(List<Msg> messages, ModelConfig config) {
            return Flux.just(
                    StreamChunk.text("hi"),
                    StreamChunk.usage(inputTokens, outputTokens),
                    StreamChunk.done());
        }
    }

    private static final class RecordingTracer implements Tracer {
        final List<RecordingSpan> spans = new ArrayList<>();

        @Override
        public Span startReasoningSpan(Span parent, String modelName, int messageCount) {
            RecordingSpan span = new RecordingSpan("reasoning:" + modelName);
            spans.add(span);
            return span;
        }
    }

    private static final class RecordingSpan implements Span {
        final String name;
        final Map<String, Object> attributes = new HashMap<>();
        boolean statusSuccess;
        String statusMessage;
        boolean ended;

        RecordingSpan(String name) {
            this.name = name;
        }

        @Override
        public String spanId() {
            return "test-span";
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Span parent() {
            return NoopSpan.INSTANCE;
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public void setStatus(boolean success, String message) {
            this.statusSuccess = success;
            this.statusMessage = message;
        }

        @Override
        public void end() {
            this.ended = true;
        }
    }
}
