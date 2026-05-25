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
package io.kairo.core.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.model.StreamChunk;
import io.kairo.api.model.StreamChunkType;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

class RawOpenAISseSubscriberTest {

    @Test
    void trailingUsageFrame_emitsUsageChunkBeforeDone() {
        Sinks.Many<StreamChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
        RawOpenAISseSubscriber sub = new RawOpenAISseSubscriber(sink, new ObjectMapper());
        sub.onSubscribe(new NoopSubscription());

        // First a normal text chunk
        sub.onNext("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"},\"finish_reason\":null}]}");
        // Then OpenAI/GLM trailing usage frame with EMPTY choices
        sub.onNext(
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":42,\"completion_tokens\":7}}");
        sub.onNext("data: [DONE]");

        List<StreamChunk> chunks = sink.asFlux().collectList().block();
        assertThat(chunks).isNotNull();

        // We expect TEXT → USAGE → DONE (the order matters: USAGE before DONE so downstream
        // can attach token counts before the stream terminates)
        assertThat(chunks)
                .extracting(StreamChunk::type)
                .containsExactly(StreamChunkType.TEXT, StreamChunkType.USAGE, StreamChunkType.DONE);

        StreamChunk usage = chunks.get(1);
        assertThat(usage.metadata())
                .containsEntry("gen_ai.usage.input_tokens", 42)
                .containsEntry("gen_ai.usage.output_tokens", 7);
    }

    @Test
    void usageWithBothZero_doesNotEmitUsageChunk() {
        Sinks.Many<StreamChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
        RawOpenAISseSubscriber sub = new RawOpenAISseSubscriber(sink, new ObjectMapper());
        sub.onSubscribe(new NoopSubscription());

        sub.onNext(
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":0,\"completion_tokens\":0}}");
        sub.onNext("data: [DONE]");

        List<StreamChunk> chunks = sink.asFlux().collectList().block();
        assertThat(chunks).isNotNull();
        assertThat(chunks).extracting(StreamChunk::type).containsExactly(StreamChunkType.DONE);
    }

    @Test
    void noUsageField_doesNotEmitUsageChunk() {
        Sinks.Many<StreamChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
        RawOpenAISseSubscriber sub = new RawOpenAISseSubscriber(sink, new ObjectMapper());
        sub.onSubscribe(new NoopSubscription());

        sub.onNext(
                "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":\"stop\"}]}");

        List<StreamChunk> chunks = sink.asFlux().collectList().block();
        assertThat(chunks).isNotNull();
        assertThat(chunks)
                .extracting(StreamChunk::type)
                .containsExactly(StreamChunkType.TEXT, StreamChunkType.DONE);
    }

    private static final class NoopSubscription implements Flow.Subscription {
        @Override
        public void request(long n) {}

        @Override
        public void cancel() {}
    }
}
