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
package io.kairo.core.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ApiErrorType;
import io.kairo.api.model.ApiException;
import io.kairo.api.model.ClassifiedError;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.model.ApiErrorClassifierImpl;
import io.kairo.core.model.ModelFallbackManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ErrorRecoveryStrategyTest {

    private ModelProvider modelProvider;
    private ModelFallbackManager fallbackManager;
    private ErrorRecoveryStrategy strategy;
    private ModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        fallbackManager = new ModelFallbackManager(List.of("fallback-model-1"));
        // Pass null for contextManager — tests that need compaction will test truncation path
        strategy = new ErrorRecoveryStrategy(modelProvider, null, fallbackManager);
        modelConfig =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .systemPrompt("You are a helper")
                        .build();
    }

    // ---- truncateOldest tests ----

    @Test
    void truncateOldestRemovesOldMessages() {
        List<Msg> messages = new ArrayList<>();
        // Add 15 non-system messages
        for (int i = 0; i < 15; i++) {
            messages.add(Msg.of(MsgRole.USER, "message " + i));
        }

        List<Msg> truncated = strategy.truncateOldest(messages);

        // Should keep last 10 non-system messages
        assertTrue(truncated.size() < messages.size(), "Truncated should have fewer messages");
        assertEquals(10, truncated.size());
    }

    @Test
    void truncateOldestPreservesSystemMessages() {
        List<Msg> messages = new ArrayList<>();
        Msg sysMsg1 = Msg.of(MsgRole.SYSTEM, "System prompt 1");
        Msg sysMsg2 = Msg.of(MsgRole.SYSTEM, "System prompt 2");
        messages.add(sysMsg1);
        messages.add(sysMsg2);
        // Add 15 non-system messages
        for (int i = 0; i < 15; i++) {
            messages.add(Msg.of(MsgRole.USER, "user message " + i));
        }

        List<Msg> truncated = strategy.truncateOldest(messages);

        // Should keep all system messages + last 10 non-system
        assertEquals(12, truncated.size());
        // First two should be system messages
        assertEquals(MsgRole.SYSTEM, truncated.get(0).role());
        assertEquals(MsgRole.SYSTEM, truncated.get(1).role());
        // Verify the system messages are the original ones
        assertEquals(sysMsg1.id(), truncated.get(0).id());
        assertEquals(sysMsg2.id(), truncated.get(1).id());
    }

    // ---- handlePromptTooLong tests ----

    @Test
    void handlePromptTooLongTriggersCompactionViaTruncation() {
        // With null contextManager, handlePromptTooLong falls back to truncation
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            messages.add(Msg.of(MsgRole.USER, "msg " + i));
        }

        ModelResponse successResponse =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent("ok")),
                        new ModelResponse.Usage(10, 5, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");

        // After truncation, model call succeeds
        when(modelProvider.call(any(), any())).thenReturn(Mono.just(successResponse));

        Mono<ModelResponse> result = strategy.handlePromptTooLong(messages, modelConfig, 0);

        StepVerifier.create(result)
                .assertNext(resp -> assertEquals("resp-1", resp.id()))
                .verifyComplete();

        // Verify model was called with truncated messages (fewer than original 15)
        verify(modelProvider).call(argThat(msgs -> msgs.size() < 15), any());
    }

    // ---- handleRateLimited tests ----

    @Test
    void handleRateLimitedRetriesAfterDelay() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        ClassifiedError rateLimitError =
                new ClassifiedError(
                        ApiErrorType.RATE_LIMITED,
                        "Rate limited",
                        Duration.ofMillis(100),
                        Map.of());

        ModelResponse successResponse =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent("ok")),
                        new ModelResponse.Usage(10, 5, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");

        when(modelProvider.call(any(), any())).thenReturn(Mono.just(successResponse));

        Mono<ModelResponse> result =
                strategy.handleRateLimited(messages, modelConfig, rateLimitError, 0);

        StepVerifier.create(result)
                .assertNext(resp -> assertEquals("resp-1", resp.id()))
                .verifyComplete();
    }

    // ---- handleServerError tests ----

    @Test
    void handleServerErrorRetriesWithBackoff() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        ClassifiedError serverError =
                new ClassifiedError(
                        ApiErrorType.SERVER_ERROR, "Internal server error", null, Map.of());

        ModelResponse successResponse =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent("ok")),
                        new ModelResponse.Usage(10, 5, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");

        // The fallback manager has a fallback, so it should use it
        when(modelProvider.call(any(), any())).thenReturn(Mono.just(successResponse));

        Mono<ModelResponse> result =
                strategy.handleServerError(messages, modelConfig, serverError, 0);

        StepVerifier.create(result).assertNext(resp -> assertNotNull(resp)).verifyComplete();
    }

    // ---- handleMaxOutputTokens tests ----

    @Test
    void handleMaxOutputTokensContinues() {
        List<Msg> messages = new ArrayList<>();
        messages.add(Msg.of(MsgRole.USER, "Write a long essay"));

        ModelResponse successResponse =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent("continued response")),
                        new ModelResponse.Usage(10, 5, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");

        when(modelProvider.call(any(), any())).thenReturn(Mono.just(successResponse));

        Mono<ModelResponse> result = strategy.handleMaxOutputTokens(messages, modelConfig, 0);

        StepVerifier.create(result)
                .assertNext(
                        resp -> {
                            assertEquals("resp-1", resp.id());
                        })
                .verifyComplete();

        // Verify model was called with an extra continuation message
        verify(modelProvider)
                .call(
                        argThat(
                                msgs -> {
                                    // Original message + continuation request
                                    return msgs.size() == 2
                                            && msgs.get(1).role() == MsgRole.USER
                                            && msgs.get(1).text().contains("truncated");
                                }),
                        any());
    }

    // ---- callModelWithRecovery tests ----

    @Test
    void callModelWithRecoveryClassifiesErrors() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        // First call fails with auth error (non-retryable)
        ApiException authError =
                new ApiException(ApiErrorType.AUTHENTICATION_ERROR, "Invalid API key", Map.of());
        when(modelProvider.call(any(), any())).thenReturn(Mono.error(authError));

        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .expectErrorMatches(
                        e ->
                                e instanceof ApiException
                                        && ((ApiException) e).getErrorType()
                                                == ApiErrorType.AUTHENTICATION_ERROR)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void maxRetriesExhaustedReturnsError() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        RuntimeException serverError = new RuntimeException("Server error");
        when(modelProvider.call(any(), any())).thenReturn(Mono.error(serverError));

        // Start at MAX_RETRY_ATTEMPTS so the next error exceeds the limit
        Mono<ModelResponse> result =
                strategy.callModelWithRecovery(
                        messages, modelConfig, ErrorRecoveryStrategy.MAX_RETRY_ATTEMPTS);

        StepVerifier.create(result)
                .expectErrorMatches(e -> e.getMessage().equals("Server error"))
                .verify(Duration.ofSeconds(5));

        // Should have been called exactly once (no retries since max already reached)
        verify(modelProvider, times(1)).call(any(), any());
    }

    @Test
    void callModelWithRecoveryBudgetExceededNotRetried() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        ApiException budgetError =
                new ApiException(ApiErrorType.BUDGET_EXCEEDED, "Budget exceeded", Map.of());
        when(modelProvider.call(any(), any())).thenReturn(Mono.error(budgetError));

        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .expectErrorMatches(
                        e ->
                                e instanceof ApiException
                                        && ((ApiException) e).getErrorType()
                                                == ApiErrorType.BUDGET_EXCEEDED)
                .verify(Duration.ofSeconds(5));

        // Should only call once — budget exceeded is not retryable
        verify(modelProvider, times(1)).call(any(), any());
    }

    @Test
    void fallbackManagerAccessor() {
        assertSame(fallbackManager, strategy.fallbackManager());
    }

    // ---- New: end-to-end error recovery tests ----

    @Test
    void retriesOnRateLimitError() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        ModelResponse successResponse =
                new ModelResponse(
                        "resp-ok",
                        List.of(new Content.TextContent("Success after rate limit")),
                        new ModelResponse.Usage(10, 5, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");

        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(any(), any()))
                .thenAnswer(
                        inv -> {
                            int n = callCount.getAndIncrement();
                            if (n == 0) {
                                return Mono.error(
                                        new RuntimeException(
                                                "429 rate limited. Retry after 1 seconds"));
                            }
                            return Mono.just(successResponse);
                        });

        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .assertNext(
                        resp -> {
                            assertEquals("resp-ok", resp.id());
                            assertTrue(
                                    resp.contents().get(0) instanceof Content.TextContent tc
                                            && tc.text().contains("Success after rate limit"));
                        })
                .verifyComplete();

        assertEquals(2, callCount.get(), "Provider should have been called exactly twice");
    }

    @Test
    void retriesOnServerError() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        ModelResponse successResponse =
                new ModelResponse(
                        "resp-ok",
                        List.of(new Content.TextContent("Recovered from server error")),
                        new ModelResponse.Usage(10, 5, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");

        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(any(), any()))
                .thenAnswer(
                        inv -> {
                            int n = callCount.getAndIncrement();
                            if (n == 0) {
                                return Mono.error(
                                        new RuntimeException("500 internal server error"));
                            }
                            return Mono.just(successResponse);
                        });

        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .assertNext(resp -> assertEquals("resp-ok", resp.id()))
                .verifyComplete();

        assertTrue(callCount.get() >= 2, "Provider should have been called at least twice");
    }

    @Test
    void fallsBackOnMaxRetriesExceeded() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        // Always fail with server error
        when(modelProvider.call(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("500 internal server error")));

        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .expectErrorMatches(
                        e ->
                                e instanceof RuntimeException
                                        && e.getMessage().contains("500 internal server error"))
                .verify(Duration.ofSeconds(30));
    }

    @Test
    void classifiesTransientVsPermanentErrors() {
        ApiErrorClassifierImpl classifier = new ApiErrorClassifierImpl();

        // 429 = RATE_LIMITED (transient/retryable)
        ClassifiedError rateLimited = classifier.classify(new RuntimeException("429 too many requests"));
        assertEquals(ApiErrorType.RATE_LIMITED, rateLimited.type());

        // 400 = UNKNOWN (permanent, not retryable by classifier)
        ClassifiedError badRequest = classifier.classify(new RuntimeException("400 bad request"));
        assertEquals(
                ApiErrorType.UNKNOWN,
                badRequest.type(),
                "400 bad request should be classified as UNKNOWN (permanent)");

        // 500 = SERVER_ERROR (transient/retryable)
        ClassifiedError serverError =
                classifier.classify(new RuntimeException("500 internal server error"));
        assertEquals(ApiErrorType.SERVER_ERROR, serverError.type());

        // 401 = AUTHENTICATION_ERROR (permanent)
        ClassifiedError authError = classifier.classify(new RuntimeException("401 unauthorized"));
        assertEquals(ApiErrorType.AUTHENTICATION_ERROR, authError.type());

        // Already-classified ApiException should pass through
        ApiException apiEx =
                new ApiException(ApiErrorType.BUDGET_EXCEEDED, "Budget exceeded", Map.of());
        ClassifiedError budget = classifier.classify(apiEx);
        assertEquals(ApiErrorType.BUDGET_EXCEEDED, budget.type());
    }

    @Test
    void noRetryOnPermanentError() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        // 401 authentication error is permanent — should not retry
        ApiException authError =
                new ApiException(
                        ApiErrorType.AUTHENTICATION_ERROR, "Invalid API key", Map.of());
        when(modelProvider.call(any(), any())).thenReturn(Mono.error(authError));

        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .expectErrorMatches(
                        e ->
                                e instanceof ApiException
                                        && ((ApiException) e).getErrorType()
                                                == ApiErrorType.AUTHENTICATION_ERROR)
                .verify(Duration.ofSeconds(5));

        // Should only be called once — no retry on permanent error
        verify(modelProvider, times(1)).call(any(), any());
    }

    @Test
    void exponentialBackoffBetweenRetries() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));

        ModelResponse successResponse =
                new ModelResponse(
                        "resp-ok",
                        List.of(new Content.TextContent("ok")),
                        new ModelResponse.Usage(10, 5, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");

        // Fail twice with server error, then succeed
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(any(), any()))
                .thenAnswer(
                        inv -> {
                            int n = callCount.getAndIncrement();
                            if (n < 2) {
                                return Mono.error(
                                        new RuntimeException("500 internal server error"));
                            }
                            return Mono.just(successResponse);
                        });

        long start = System.currentTimeMillis();
        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .assertNext(resp -> assertEquals("resp-ok", resp.id()))
                .verifyComplete();

        long elapsed = System.currentTimeMillis() - start;
        assertTrue(callCount.get() >= 3, "Should have been called at least 3 times");
        // Exponential backoff: 1s (2^0) + 2s (2^1) = 3s minimum
        // Allow some slack but verify there was meaningful delay
        assertTrue(
                elapsed >= 2000,
                "Exponential backoff should have caused at least 2s delay, but was " + elapsed + "ms");
    }
}
