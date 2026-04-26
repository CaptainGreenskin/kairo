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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CompactionModelForkTest {

    private static ModelResponse responseWithText(String text) {
        var content = new Content.TextContent(text);
        return new ModelResponse("id", List.of(content), null, null, "model");
    }

    @Test
    void summarize_returnsTextFromProvider() {
        var provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic");
        when(provider.call(anyList(), any())).thenReturn(Mono.just(responseWithText("summary")));

        var fork = new CompactionModelFork(provider);
        StepVerifier.create(fork.summarize(List.of(), "summarize this"))
                .expectNext("summary")
                .verifyComplete();
    }

    @Test
    void summarize_anthropicProvider_usesDefaultModel() {
        var provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic-claude");
        when(provider.call(
                        anyList(), argThat(cfg -> cfg.model().equals(ModelConfig.DEFAULT_MODEL))))
                .thenReturn(Mono.just(responseWithText("ok")));

        StepVerifier.create(new CompactionModelFork(provider).summarize(List.of(), "prompt"))
                .expectNext("ok")
                .verifyComplete();
    }

    @Test
    void summarize_nonAnthropicProvider_usesGpt4oMini() {
        var provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("openai");
        when(provider.call(anyList(), argThat(cfg -> cfg.model().equals("gpt-4o-mini"))))
                .thenReturn(Mono.just(responseWithText("ok")));

        StepVerifier.create(new CompactionModelFork(provider).summarize(List.of(), "prompt"))
                .expectNext("ok")
                .verifyComplete();
    }

    @Test
    void summarize_buildsSystemAndUserMessages() {
        var provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic");

        var capturedMessages = new java.util.ArrayList<List<Msg>>();
        when(provider.call(anyList(), any()))
                .thenAnswer(
                        inv -> {
                            capturedMessages.add(inv.getArgument(0));
                            return Mono.just(responseWithText("done"));
                        });

        var inputMsg = Msg.of(MsgRole.USER, "hello");
        new CompactionModelFork(provider).summarize(List.of(inputMsg), "my prompt").block();

        assertThat(capturedMessages).hasSize(1);
        var msgs = capturedMessages.get(0);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).role()).isEqualTo(MsgRole.SYSTEM);
        assertThat(msgs.get(1).role()).isEqualTo(MsgRole.USER);
        assertThat(msgs.get(1).text()).contains("hello");
    }
}
