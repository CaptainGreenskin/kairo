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
package io.kairo.examples.demo;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST controller demonstrating Server-Sent Events (SSE) streaming with Kairo's {@link
 * ModelProvider#stream(List, ModelConfig)} API.
 *
 * <p>Streams partial model responses as SSE events, allowing clients to display tokens
 * incrementally as they arrive from the LLM provider. This is the preferred pattern for interactive
 * chat UIs where perceived latency matters.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * curl -N "http://localhost:8080/stream/chat?message=Tell+me+a+joke"
 * }</pre>
 */
@RestController
public class StreamingChatController {

    private final ModelProvider modelProvider;

    public StreamingChatController(ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    /**
     * Stream a chat response as Server-Sent Events.
     *
     * <p>Calls {@link ModelProvider#stream(List, ModelConfig)} and emits each partial text chunk as
     * an SSE {@code data} event. A final {@code [DONE]} event signals that the stream has
     * completed.
     *
     * @param message the user's input message
     * @return a Flux of SSE events containing text chunks
     */
    @GetMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(
            @RequestParam(defaultValue = "Hello!") String message) {

        ModelConfig config =
                ModelConfig.builder()
                        .model(
                                modelProvider.name().equals("anthropic")
                                        ? ModelConfig.DEFAULT_MODEL
                                        : "qwen-plus")
                        .maxTokens(ModelConfig.DEFAULT_MAX_TOKENS)
                        .temperature(0.7)
                        .systemPrompt("You are a helpful assistant. Keep responses concise.")
                        .build();

        Msg userMsg = Msg.of(MsgRole.USER, message);

        Flux<ServerSentEvent<String>> dataEvents =
                modelProvider.stream(List.of(userMsg), config)
                        .map(this::extractText)
                        .filter(text -> !text.isEmpty())
                        .map(text -> ServerSentEvent.<String>builder().data(text).build());

        Flux<ServerSentEvent<String>> doneEvent =
                Flux.just(ServerSentEvent.<String>builder().data("[DONE]").build());

        return dataEvents
                .concatWith(doneEvent)
                .onErrorResume(
                        e ->
                                Flux.just(
                                        ServerSentEvent.<String>builder()
                                                .event("error")
                                                .data("Error: " + e.getMessage())
                                                .build()));
    }

    /** Extract the first text content from a partial model response. */
    private String extractText(ModelResponse response) {
        if (response == null || response.contents() == null) {
            return "";
        }
        return response.contents().stream()
                .filter(Content.TextContent.class::isInstance)
                .map(Content.TextContent.class::cast)
                .map(Content.TextContent::text)
                .findFirst()
                .orElse("");
    }
}
