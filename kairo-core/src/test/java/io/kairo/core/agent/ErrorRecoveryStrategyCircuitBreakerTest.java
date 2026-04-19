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
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.model.CircuitBreakerOpenException;
import io.kairo.core.model.ModelCircuitBreaker;
import io.kairo.core.model.ModelFallbackManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ErrorRecoveryStrategyCircuitBreakerTest {

    private ModelProvider modelProvider;
    private ModelFallbackManager fallbackManager;
    private ModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        fallbackManager = new ModelFallbackManager(List.of("fallback-model-1"));
        modelConfig =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .systemPrompt("You are a helper")
                        .build();
    }

    private ModelResponse successResponse() {
        return new ModelResponse(
                "resp-ok",
                List.of(new Content.TextContent("ok")),
                new ModelResponse.Usage(10, 5, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    @Test
    void testCircuitBreakerOpenThrowsException() {
        // Use threshold=1 so a single failure opens the breaker
        ModelCircuitBreaker breaker =
                new ModelCircuitBreaker("test-model", 1, Duration.ofSeconds(60));
        // Force the breaker to OPEN state
        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.OPEN, breaker.getState());

        ErrorRecoveryStrategy strategy =
                new ErrorRecoveryStrategy(modelProvider, null, fallbackManager, breaker);

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));
        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .expectErrorMatches(
                        e ->
                                e instanceof CircuitBreakerOpenException
                                        && ((CircuitBreakerOpenException) e)
                                                .getModelId()
                                                .equals("test-model"))
                .verify(Duration.ofSeconds(5));

        // Model should never have been called
        verifyNoInteractions(modelProvider);
    }

    @Test
    void testTransientErrorRecordedInBreaker() {
        // Threshold=2 so we can observe the failure count increase
        ModelCircuitBreaker breaker =
                new ModelCircuitBreaker("test-model", 2, Duration.ofSeconds(60));
        ErrorRecoveryStrategy strategy =
                new ErrorRecoveryStrategy(modelProvider, null, fallbackManager, breaker);

        assertEquals(ModelCircuitBreaker.State.CLOSED, breaker.getState());

        // Throw a 500 server error — classified as SERVER_ERROR (transient)
        when(modelProvider.call(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("500 internal server error")));

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));
        // Start at MAX_RETRY_ATTEMPTS so it won't retry, just classify and record
        Mono<ModelResponse> result =
                strategy.callModelWithRecovery(
                        messages, modelConfig, ErrorRecoveryStrategy.MAX_RETRY_ATTEMPTS);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify(Duration.ofSeconds(5));

        // The error hit max retries so was not retried, but we need to check
        // that failure was NOT recorded when max retries are reached (it short-circuits).
        // Actually, looking at the code: max retries check happens first, so recordFailure
        // is not called. Let's test with retryCount=0 instead to trigger classification.
        // We need to allow retries but eventually exhaust them.
        // Let's use retryCount=0 and verify the breaker state after exhaustion.

        // Reset: use a fresh breaker
        ModelCircuitBreaker breaker2 =
                new ModelCircuitBreaker("test-model", 2, Duration.ofSeconds(60));
        ErrorRecoveryStrategy strategy2 =
                new ErrorRecoveryStrategy(modelProvider, null, fallbackManager, breaker2);

        // Always fail with server error — will retry with backoff until max retries
        when(modelProvider.call(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("500 internal server error")));

        Mono<ModelResponse> result2 = strategy2.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result2)
                .expectError(RuntimeException.class)
                .verify(Duration.ofSeconds(30));

        // After multiple transient failures, the breaker should have opened
        assertEquals(
                ModelCircuitBreaker.State.OPEN,
                breaker2.getState(),
                "Circuit breaker should be OPEN after multiple transient SERVER_ERROR failures");
    }

    @Test
    void testNonTransientErrorNotRecorded() {
        ModelCircuitBreaker breaker =
                new ModelCircuitBreaker("test-model", 2, Duration.ofSeconds(60));
        ErrorRecoveryStrategy strategy =
                new ErrorRecoveryStrategy(modelProvider, null, fallbackManager, breaker);

        // AUTHENTICATION_ERROR is non-transient — should not be recorded in breaker
        ApiException authError =
                new ApiException(ApiErrorType.AUTHENTICATION_ERROR, "Invalid API key", Map.of());
        when(modelProvider.call(any(), any())).thenReturn(Mono.error(authError));

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));
        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .expectErrorMatches(
                        e ->
                                e instanceof ApiException
                                        && ((ApiException) e).getErrorType()
                                                == ApiErrorType.AUTHENTICATION_ERROR)
                .verify(Duration.ofSeconds(5));

        // Circuit breaker should still be CLOSED — auth errors are not transient
        assertEquals(
                ModelCircuitBreaker.State.CLOSED,
                breaker.getState(),
                "Circuit breaker should remain CLOSED for non-transient AUTHENTICATION_ERROR");
    }

    @Test
    void testSuccessResetsBreaker() {
        ModelCircuitBreaker breaker =
                new ModelCircuitBreaker("test-model", 5, Duration.ofSeconds(60));
        // Record some failures (but not enough to open)
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.CLOSED, breaker.getState());

        ErrorRecoveryStrategy strategy =
                new ErrorRecoveryStrategy(modelProvider, null, fallbackManager, breaker);

        when(modelProvider.call(any(), any())).thenReturn(Mono.just(successResponse()));

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));
        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .assertNext(resp -> assertEquals("resp-ok", resp.id()))
                .verifyComplete();

        // After success, breaker should be CLOSED and failure count reset
        assertEquals(ModelCircuitBreaker.State.CLOSED, breaker.getState());

        // Verify reset: we can now record (threshold-1) failures without opening
        // If recordSuccess didn't reset, those 2 prior failures + 3 more = 5 = threshold → OPEN
        // With reset, we need 5 fresh failures to open
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(
                ModelCircuitBreaker.State.CLOSED,
                breaker.getState(),
                "After success reset, 3 failures should not open breaker (threshold=5)");
    }

    @Test
    void testNullCircuitBreakerBackwardCompat() {
        // Use the old 3-arg constructor (no circuit breaker)
        ErrorRecoveryStrategy strategy =
                new ErrorRecoveryStrategy(modelProvider, null, fallbackManager);

        when(modelProvider.call(any(), any())).thenReturn(Mono.just(successResponse()));

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));
        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        // Should work without NPE
        StepVerifier.create(result)
                .assertNext(resp -> assertEquals("resp-ok", resp.id()))
                .verifyComplete();
    }

    @Test
    void testCircuitBreakerHalfOpenProbeSucceeds() throws InterruptedException {
        // Very short timeout so we can transition to HALF_OPEN quickly
        ModelCircuitBreaker breaker =
                new ModelCircuitBreaker("test-model", 1, Duration.ofMillis(100));

        // Open the breaker
        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.OPEN, breaker.getState());

        // Wait for the reset timeout to elapse
        Thread.sleep(150);

        // Next allowCall() should transition OPEN → HALF_OPEN
        ErrorRecoveryStrategy strategy =
                new ErrorRecoveryStrategy(modelProvider, null, fallbackManager, breaker);

        when(modelProvider.call(any(), any())).thenReturn(Mono.just(successResponse()));

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "hello"));
        Mono<ModelResponse> result = strategy.callModelWithRecovery(messages, modelConfig, 0);

        StepVerifier.create(result)
                .assertNext(resp -> assertEquals("resp-ok", resp.id()))
                .verifyComplete();

        // Success on probe call should transition HALF_OPEN → CLOSED
        assertEquals(
                ModelCircuitBreaker.State.CLOSED,
                breaker.getState(),
                "Circuit breaker should transition to CLOSED after successful probe in HALF_OPEN");
    }
}
