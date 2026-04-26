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
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

class OpenAISseSubscriberTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Sinks.Many<ModelResponse> sink;
    private OpenAISseSubscriber subscriber;
    private List<ModelResponse> collected;

    @BeforeEach
    void setUp() {
        sink = Sinks.many().multicast().directBestEffort();
        subscriber = new OpenAISseSubscriber(sink, MAPPER);
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
    void nonDataLine_isIgnored() {
        subscriber.onNext("event: message_start");
        assertThat(collected).isEmpty();
    }

    @Test
    void done_emitsFinalResponseAndCompletes() {
        subscriber.onNext(
                "data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\","
                        + "\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"index\":0,"
                        + "\"finish_reason\":null}]}");
        subscriber.onNext("data: [DONE]");

        // Final response emitted by [DONE]
        var last = collected.get(collected.size() - 1);
        assertThat(last.id()).isEqualTo("chatcmpl-1");
        boolean hasFullText =
                last.contents().stream()
                        .anyMatch(
                                c ->
                                        c instanceof Content.TextContent tc
                                                && tc.text().equals("Hello"));
        assertThat(hasFullText).isTrue();
    }

    @Test
    void textDelta_emitsPartialResponse() {
        subscriber.onNext(
                "data: {\"id\":\"chatcmpl-2\",\"model\":\"gpt-4o\","
                        + "\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"index\":0,"
                        + "\"finish_reason\":null}]}");

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
    void stopReason_endTurn_onStopFinishReason() {
        subscriber.onNext(
                "data: {\"id\":\"chatcmpl-3\",\"model\":\"gpt-4o\","
                        + "\"choices\":[{\"delta\":{\"content\":\"Bye\"},\"index\":0,"
                        + "\"finish_reason\":\"stop\"}]}");
        subscriber.onNext("data: [DONE]");

        var last = collected.get(collected.size() - 1);
        assertThat(last.stopReason()).isEqualTo(ModelResponse.StopReason.END_TURN);
    }

    @Test
    void toolCallDelta_accumulatesAndEmitsOnDone() {
        subscriber.onNext(
                "data: {\"id\":\"chatcmpl-4\",\"model\":\"gpt-4o\","
                        + "\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"tc-1\","
                        + "\"function\":{\"name\":\"calculator\",\"arguments\":\"{}\"}}]},"
                        + "\"finish_reason\":\"tool_calls\"}]}");
        subscriber.onNext("data: [DONE]");

        var last = collected.get(collected.size() - 1);
        assertThat(last.stopReason()).isEqualTo(ModelResponse.StopReason.TOOL_USE);
        boolean hasToolUse =
                last.contents().stream()
                        .anyMatch(
                                c ->
                                        c instanceof Content.ToolUseContent tu
                                                && "calculator".equals(tu.toolName())
                                                && "tc-1".equals(tu.toolId()));
        assertThat(hasToolUse).isTrue();
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
