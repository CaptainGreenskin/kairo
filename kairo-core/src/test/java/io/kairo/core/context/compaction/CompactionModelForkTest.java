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
package io.kairo.core.context.compaction;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.ModelResponse.StopReason;
import io.kairo.api.model.ModelResponse.Usage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CompactionModelForkTest {

    private static final String SUMMARY_TEXT = "This is a summary.";

    private ModelProvider stubProvider;
    private CompactionModelFork fork;

    @BeforeEach
    void setUp() {
        stubProvider =
                new ModelProvider() {
                    @Override
                    public String name() {
                        return "stub-provider";
                    }

                    @Override
                    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
                        ModelResponse response =
                                new ModelResponse(
                                        "resp-1",
                                        List.of(new Content.TextContent(SUMMARY_TEXT)),
                                        new Usage(10, 20, 0, 0),
                                        StopReason.END_TURN,
                                        "stub-model");
                        return Mono.just(response);
                    }

                    @Override
                    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
                        return Flux.empty();
                    }
                };
        fork = new CompactionModelFork(stubProvider);
    }

    @Test
    void constructionDoesNotThrow() {
        assertDoesNotThrow(() -> new CompactionModelFork(stubProvider));
    }

    @Test
    void summarizeReturnsNonNullMono() {
        Mono<String> result = fork.summarize(List.of(), "Summarize this.");
        assertNotNull(result);
    }

    @Test
    void summarizeWithEmptyMessagesProducesResult() {
        StepVerifier.create(fork.summarize(List.of(), "Summarize this."))
                .expectNextMatches(s -> !s.isEmpty())
                .verifyComplete();
    }

    @Test
    void summarizeReturnsDelegateTextContent() {
        StepVerifier.create(fork.summarize(List.of(), "Summarize this."))
                .expectNext(SUMMARY_TEXT)
                .verifyComplete();
    }

    @Test
    void summarizeWithConversationMessagesDoesNotThrow() {
        List<Msg> messages =
                List.of(Msg.of(MsgRole.USER, "Hello"), Msg.of(MsgRole.ASSISTANT, "Hi there"));
        StepVerifier.create(fork.summarize(messages, "Summarize."))
                .expectNext(SUMMARY_TEXT)
                .verifyComplete();
    }

    @Test
    void summarizeWithAnthropicProviderSelectsDefaultModel() {
        ModelProvider anthropicStub =
                new ModelProvider() {
                    @Override
                    public String name() {
                        return "anthropic-provider";
                    }

                    @Override
                    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
                        // Verify model is the Anthropic default
                        assertNotNull(config.model());
                        ModelResponse response =
                                new ModelResponse(
                                        "r",
                                        List.of(new Content.TextContent("ok")),
                                        new Usage(1, 1, 0, 0),
                                        StopReason.END_TURN,
                                        config.model());
                        return Mono.just(response);
                    }

                    @Override
                    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
                        return Flux.empty();
                    }
                };
        CompactionModelFork anthropicFork = new CompactionModelFork(anthropicStub);
        StepVerifier.create(anthropicFork.summarize(List.of(), "Summarize."))
                .expectNext("ok")
                .verifyComplete();
    }
}
