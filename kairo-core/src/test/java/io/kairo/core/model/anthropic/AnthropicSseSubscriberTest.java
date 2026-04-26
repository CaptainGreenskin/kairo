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
package io.kairo.core.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

class AnthropicSseSubscriberTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Sinks.Many<ModelResponse> sink;
    private AnthropicSseSubscriber subscriber;
    private List<ModelResponse> collected;

    @BeforeEach
    void setUp() {
        sink = Sinks.many().multicast().directBestEffort();
        subscriber = new AnthropicSseSubscriber(sink, MAPPER);
        collected = new ArrayList<>();
        sink.asFlux().subscribe(collected::add);
    }

    @Test
    void blankLine_isIgnored() {
        subscriber.onNext("");
        subscriber.onNext("  ");
        assertThat(collected).isEmpty();
    }

    @Test
    void eventLine_isIgnored() {
        subscriber.onNext("event: message_start");
        assertThat(collected).isEmpty();
    }

    @Test
    void done_completesWithoutEmittingResponse() {
        subscriber.onNext("data: [DONE]");
        assertThat(collected).isEmpty();
    }

    @Test
    void messageStartThenStop_emitsFinalResponse() {
        subscriber.onNext(
                "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg-1\",\"model\":\"claude-3\","
                        + "\"usage\":{\"input_tokens\":10,\"output_tokens\":0,"
                        + "\"cache_read_input_tokens\":0,\"cache_creation_input_tokens\":0}}}");
        subscriber.onNext(
                "data: {\"type\":\"content_block_start\",\"index\":0,"
                        + "\"content_block\":{\"type\":\"text\"}}");
        subscriber.onNext(
                "data: {\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}");
        subscriber.onNext("data: {\"type\":\"content_block_stop\",\"index\":0}");
        subscriber.onNext(
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},"
                        + "\"usage\":{\"output_tokens\":5}}");
        subscriber.onNext("data: {\"type\":\"message_stop\"}");

        // Last response is the final assembled one
        var finalResponse = collected.get(collected.size() - 1);
        assertThat(finalResponse.id()).isEqualTo("msg-1");
        assertThat(finalResponse.model()).isEqualTo("claude-3");
        assertThat(finalResponse.stopReason()).isEqualTo(ModelResponse.StopReason.END_TURN);
    }

    @Test
    void textDelta_emitsPartialResponse() {
        subscriber.onNext(
                "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg-1\",\"model\":\"claude-3\","
                        + "\"usage\":{\"input_tokens\":5,\"output_tokens\":0,"
                        + "\"cache_read_input_tokens\":0,\"cache_creation_input_tokens\":0}}}");
        subscriber.onNext(
                "data: {\"type\":\"content_block_start\",\"index\":0,"
                        + "\"content_block\":{\"type\":\"text\"}}");
        subscriber.onNext(
                "data: {\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}");

        // Partial responses are emitted for each text delta
        boolean hasPartial =
                collected.stream()
                        .anyMatch(
                                r ->
                                        r.contents().stream()
                                                .anyMatch(
                                                        c ->
                                                                c instanceof Content.TextContent tc
                                                                        && tc.text().equals("Hi")));
        assertThat(hasPartial).isTrue();
    }

    @Test
    void onError_propagatesToSink() {
        List<Throwable> errors = new ArrayList<>();
        sink.asFlux().subscribe(r -> {}, errors::add);
        subscriber.onError(new RuntimeException("stream error"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).hasMessage("stream error");
    }
}
