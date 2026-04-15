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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.ContextState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AutoCompactionV2Test {

    private final CompactionConfig config = new CompactionConfig(100_000, true, null);

    private ModelProvider mockProvider(String summaryText) {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic");
        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent(summaryText)),
                        new ModelResponse.Usage(1000, 500, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "claude-sonnet-4-20250514");
        when(provider.call(any(), any())).thenReturn(Mono.just(response));
        return provider;
    }

    private List<Msg> sampleMessages(int count) {
        List<Msg> msgs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            msgs.add(
                    Msg.builder()
                            .id("msg-" + i)
                            .role(i % 2 == 0 ? MsgRole.USER : MsgRole.ASSISTANT)
                            .addContent(new Content.TextContent("Message content " + i))
                            .tokenCount(100)
                            .build());
        }
        return msgs;
    }

    @Test
    @DisplayName("Uses CompactionModelFork for isolated summarization call")
    void testUsesCompactionModelFork() {
        ModelProvider provider = mockProvider("Summary of conversation");
        AutoCompaction auto = new AutoCompaction(provider);

        StepVerifier.create(auto.compact(sampleMessages(10), config))
                .assertNext(
                        result -> {
                            assertTrue(result.tokensSaved() > 0);
                            // Should contain the summary in a SYSTEM message
                            boolean hasSummary =
                                    result.compactedMessages().stream()
                                            .anyMatch(
                                                    m ->
                                                            m.text()
                                                                    .contains(
                                                                            "[Auto-compacted summary]"));
                            assertTrue(hasSummary);
                        })
                .verifyComplete();

        // Verify the provider was called (via CompactionModelFork)
        verify(provider).call(any(), any());
    }

    @Test
    @DisplayName("Retry on prompt_too_long: truncates and retries")
    void testRetryOnPromptTooLong() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic");

        // First call fails with prompt_too_long
        // Second call succeeds
        ModelResponse successResponse =
                new ModelResponse(
                        "resp-2",
                        List.of(new Content.TextContent("Truncated summary")),
                        new ModelResponse.Usage(500, 200, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "claude-sonnet-4-20250514");

        when(provider.call(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("prompt_too_long: exceeded limit")))
                .thenReturn(Mono.just(successResponse));

        AutoCompaction auto = new AutoCompaction(provider);

        StepVerifier.create(auto.compact(sampleMessages(10), config))
                .assertNext(
                        result -> {
                            boolean hasSummary =
                                    result.compactedMessages().stream()
                                            .anyMatch(
                                                    m ->
                                                            m.text()
                                                                    .contains(
                                                                            "[Auto-compacted summary]"));
                            assertTrue(hasSummary);
                        })
                .verifyComplete();

        // Should have been called twice (first failed, second succeeded)
        verify(provider, times(2)).call(any(), any());
    }

    @Test
    @DisplayName("Max 3 retries then returns original messages")
    void testMaxRetriesThenReturnsOriginal() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic");

        // All calls fail with prompt_too_long
        when(provider.call(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("prompt_too_long")));

        AutoCompaction auto = new AutoCompaction(provider);
        List<Msg> original = sampleMessages(10);

        StepVerifier.create(auto.compact(original, config))
                .assertNext(
                        result -> {
                            // After 3 retries, returns original messages with 0 tokens saved
                            assertEquals(0, result.tokensSaved());
                        })
                .verifyComplete();

        // 3 attempts (initial + 2 retries truncating)
        verify(provider, times(3)).call(any(), any());
    }

    @Test
    @DisplayName("20K token limit in CompactionModelFork config")
    void testTokenLimitInForkConfig() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic");

        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent("Summary")),
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "claude-sonnet-4-20250514");
        when(provider.call(any(), any())).thenReturn(Mono.just(response));

        AutoCompaction auto = new AutoCompaction(provider);
        auto.compact(sampleMessages(5), config).block();

        // Verify the ModelConfig passed to provider has maxTokens = 20480
        verify(provider).call(any(), argThat(cfg -> cfg.maxTokens() == 20480));
    }

    @Test
    @DisplayName("Structured XML prompt contains 9 dimensions")
    void testPromptContains9Dimensions() {
        // We can verify via the call that the system prompt includes 9 dimensions
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic");

        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent("Summary")),
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "claude-sonnet-4-20250514");
        when(provider.call(any(), any())).thenReturn(Mono.just(response));

        AutoCompaction auto = new AutoCompaction(provider);
        auto.compact(sampleMessages(5), config).block();

        // Capture the messages sent to the provider
        verify(provider)
                .call(
                        argThat(
                                messages -> {
                                    // The first message should be the SYSTEM prompt with 9
                                    // dimensions
                                    String systemText = messages.get(0).text();
                                    return systemText.contains("1.")
                                            && systemText.contains("2.")
                                            && systemText.contains("3.")
                                            && systemText.contains("4.")
                                            && systemText.contains("5.")
                                            && systemText.contains("6.")
                                            && systemText.contains("7.")
                                            && systemText.contains("8.")
                                            && systemText.contains("9.")
                                            && systemText.contains("analysis_dimensions");
                                }),
                        any());
    }

    @Test
    @DisplayName("shouldTrigger at 95% pressure with provider")
    void testShouldTriggerThreshold() {
        AutoCompaction auto = new AutoCompaction(mockProvider("test"));

        // Below threshold
        assertFalse(auto.shouldTrigger(new ContextState(100_000, 90_000, 0.90f, 10)));
        // At threshold
        assertTrue(auto.shouldTrigger(new ContextState(100_000, 95_000, 0.95f, 10)));
        // Above threshold
        assertTrue(auto.shouldTrigger(new ContextState(100_000, 98_000, 0.98f, 10)));
    }

    @Test
    @DisplayName("shouldTrigger returns false without model provider")
    void testShouldTriggerNoProvider() {
        AutoCompaction auto = new AutoCompaction(null);
        assertFalse(auto.shouldTrigger(new ContextState(100_000, 98_000, 0.98f, 10)));
    }

    @Test
    @DisplayName("compact without model provider returns original messages")
    void testCompactWithoutProvider() {
        AutoCompaction auto = new AutoCompaction(null);
        List<Msg> messages = sampleMessages(5);

        StepVerifier.create(auto.compact(messages, config))
                .assertNext(
                        result -> {
                            assertEquals(0, result.tokensSaved());
                            assertEquals(messages, result.compactedMessages());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Non-prompt_too_long error returns original messages without retry")
    void testNonRetryableError() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic");
        when(provider.call(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("rate_limit_exceeded")));

        AutoCompaction auto = new AutoCompaction(provider);

        StepVerifier.create(auto.compact(sampleMessages(5), config))
                .assertNext(
                        result -> {
                            assertEquals(0, result.tokensSaved());
                        })
                .verifyComplete();

        // Should only be called once (no retry for non-prompt_too_long errors)
        verify(provider, times(1)).call(any(), any());
    }
}
