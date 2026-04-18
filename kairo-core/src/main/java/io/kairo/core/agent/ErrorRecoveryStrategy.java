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

import io.kairo.api.context.ContextManager;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ApiErrorType;
import io.kairo.api.model.ApiException;
import io.kairo.api.model.ClassifiedError;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.model.ApiErrorClassifierImpl;
import io.kairo.core.model.CircuitBreakerOpenException;
import io.kairo.core.model.ModelCircuitBreaker;
import io.kairo.core.model.ModelFallbackManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Encapsulates error recovery strategies for the ReAct agent loop.
 *
 * <p>Handles prompt-too-long, rate limiting, server errors, and output token limits. Independently
 * testable without requiring a full agent setup.
 */
public class ErrorRecoveryStrategy {

    private static final Logger log = LoggerFactory.getLogger(ErrorRecoveryStrategy.class);

    static final int MAX_RETRY_ATTEMPTS = 3;
    static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final ApiErrorClassifierImpl errorClassifier = new ApiErrorClassifierImpl();
    private final ModelFallbackManager fallbackManager;
    private final ModelProvider modelProvider;
    private final ContextManager contextManager; // nullable
    private final ModelCircuitBreaker circuitBreaker; // nullable

    /**
     * Create a new error recovery strategy.
     *
     * @param modelProvider the model provider to use for retries
     * @param contextManager the context manager for compaction (nullable)
     * @param fallbackManager the fallback manager for model fallback
     */
    public ErrorRecoveryStrategy(
            ModelProvider modelProvider,
            ContextManager contextManager,
            ModelFallbackManager fallbackManager) {
        this(modelProvider, contextManager, fallbackManager, null);
    }

    /**
     * Create a new error recovery strategy with circuit breaker support.
     *
     * @param modelProvider the model provider to use for retries
     * @param contextManager the context manager for compaction (nullable)
     * @param fallbackManager the fallback manager for model fallback
     * @param circuitBreaker the circuit breaker for model calls (nullable)
     */
    public ErrorRecoveryStrategy(
            ModelProvider modelProvider,
            ContextManager contextManager,
            ModelFallbackManager fallbackManager,
            ModelCircuitBreaker circuitBreaker) {
        this.modelProvider = modelProvider;
        this.contextManager = contextManager;
        this.fallbackManager = fallbackManager;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Get the fallback manager for external reset on success.
     *
     * @return the model fallback manager
     */
    public ModelFallbackManager fallbackManager() {
        return fallbackManager;
    }

    /**
     * Call the model with automatic error recovery. Wraps the model call with classification-based
     * retry logic including compaction, rate-limit backoff, exponential backoff, and model
     * fallback.
     */
    public Mono<ModelResponse> callModelWithRecovery(
            List<Msg> messages, ModelConfig modelConfig, int retryCount) {
        // Circuit breaker check: reject immediately if open
        if (circuitBreaker != null && !circuitBreaker.allowCall()) {
            return Mono.error(new CircuitBreakerOpenException(circuitBreaker.getModelId()));
        }

        return modelProvider
                .call(messages, modelConfig)
                .doOnNext(
                        response -> {
                            if (circuitBreaker != null) {
                                circuitBreaker.recordSuccess();
                            }
                        })
                .onErrorResume(
                        error -> {
                            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                                log.error(
                                        "Max retry attempts ({}) reached, giving up",
                                        MAX_RETRY_ATTEMPTS);
                                return Mono.error(error);
                            }

                            var classified = errorClassifier.classify(error);
                            log.warn(
                                    "API error classified as {}: {} (attempt {}/{})",
                                    classified.type(),
                                    classified.message(),
                                    retryCount + 1,
                                    MAX_RETRY_ATTEMPTS);

                            // Record transient failures in circuit breaker
                            if (circuitBreaker != null) {
                                if (classified.type() == ApiErrorType.SERVER_ERROR
                                        || classified.type() == ApiErrorType.RATE_LIMITED) {
                                    circuitBreaker.recordFailure();
                                }
                            }

                            return switch (classified.type()) {
                                case PROMPT_TOO_LONG ->
                                        handlePromptTooLong(messages, modelConfig, retryCount);
                                case MAX_OUTPUT_TOKENS ->
                                        handleMaxOutputTokens(messages, modelConfig, retryCount);
                                case RATE_LIMITED ->
                                        handleRateLimited(
                                                messages, modelConfig, classified, retryCount);
                                case SERVER_ERROR ->
                                        handleServerError(
                                                messages, modelConfig, classified, retryCount);
                                case AUTHENTICATION_ERROR -> Mono.error(classified.toException());
                                case BUDGET_EXCEEDED -> Mono.error(classified.toException());
                                default -> Mono.error(error);
                            };
                        });
    }

    /** Handle prompt-too-long errors by attempting compaction, then truncation. */
    Mono<ModelResponse> handlePromptTooLong(
            List<Msg> messages, ModelConfig modelConfig, int retryCount) {
        log.info("Prompt too long — triggering emergency compaction");

        // 1. Try compaction via context manager if available
        if (contextManager != null) {
            return contextManager
                    .compactMessages(messages)
                    .flatMap(
                            compacted -> {
                                if (compacted != null && compacted.size() < messages.size()) {
                                    log.info(
                                            "Compacted {} → {} messages, retrying",
                                            messages.size(),
                                            compacted.size());
                                    return callModelWithRecovery(
                                            compacted, modelConfig, retryCount + 1);
                                }
                                // Compaction didn't help, try truncation
                                return truncateAndRetry(messages, modelConfig, retryCount);
                            })
                    .onErrorResume(
                            e -> {
                                log.warn(
                                        "Compaction failed: {}, falling back to truncation",
                                        e.getMessage());
                                return truncateAndRetry(messages, modelConfig, retryCount);
                            });
        }

        // 2. No context manager, try truncation directly
        return truncateAndRetry(messages, modelConfig, retryCount);
    }

    /** Truncate oldest non-system messages and retry. */
    Mono<ModelResponse> truncateAndRetry(
            List<Msg> messages, ModelConfig modelConfig, int retryCount) {
        var truncated = truncateOldest(messages);
        if (truncated.size() < messages.size()) {
            log.info("Truncated {} → {} messages, retrying", messages.size(), truncated.size());
            // Add recovery notice
            truncated.add(
                    MsgBuilder.create()
                            .role(MsgRole.SYSTEM)
                            .text(
                                    "[Context was truncated due to length."
                                            + " Some earlier conversation was removed.]")
                            .build());
            return callModelWithRecovery(truncated, modelConfig, retryCount + 1);
        }
        return Mono.error(
                new ApiException(
                        ApiErrorType.PROMPT_TOO_LONG, "Cannot reduce prompt further", Map.of()));
    }

    /** Handle max-output-tokens by adding a continuation request. */
    Mono<ModelResponse> handleMaxOutputTokens(
            List<Msg> messages, ModelConfig modelConfig, int retryCount) {
        log.info("Max output tokens hit — adding continuation request");
        var continued = new ArrayList<>(messages);
        continued.add(
                MsgBuilder.create()
                        .role(MsgRole.USER)
                        .text(
                                "Your previous response was truncated."
                                        + " Please continue where you left off.")
                        .build());
        return callModelWithRecovery(continued, modelConfig, retryCount + 1);
    }

    /** Handle rate-limiting by waiting the specified duration before retry. */
    Mono<ModelResponse> handleRateLimited(
            List<Msg> messages, ModelConfig modelConfig, ClassifiedError err, int retryCount) {
        Duration wait = err.retryAfter() != null ? err.retryAfter() : Duration.ofSeconds(5);
        log.info("Rate limited — waiting {} before retry", wait);
        return Mono.delay(wait).then(callModelWithRecovery(messages, modelConfig, retryCount + 1));
    }

    /** Handle server errors with exponential backoff and optional model fallback. */
    Mono<ModelResponse> handleServerError(
            List<Msg> messages, ModelConfig modelConfig, ClassifiedError err, int retryCount) {
        // Exponential backoff
        Duration backoff = BASE_BACKOFF.multipliedBy((long) Math.pow(2, retryCount));
        if (backoff.compareTo(MAX_BACKOFF) > 0) {
            backoff = MAX_BACKOFF;
        }
        log.info("Server error — backing off {} before retry", backoff);

        return Mono.delay(backoff)
                .then(
                        Mono.defer(
                                () -> {
                                    // Try fallback model if available
                                    if (fallbackManager.hasFallback()) {
                                        String fallback = fallbackManager.nextFallback();
                                        log.warn("Switching to fallback model: {}", fallback);
                                        ModelConfig fallbackConfig =
                                                ModelConfig.builder()
                                                        .model(fallback)
                                                        .maxTokens(modelConfig.maxTokens())
                                                        .temperature(modelConfig.temperature())
                                                        .tools(
                                                                modelConfig.tools() != null
                                                                        ? modelConfig.tools()
                                                                        : List.of())
                                                        .systemPrompt(modelConfig.systemPrompt())
                                                        .build();
                                        return callModelWithRecovery(
                                                messages, fallbackConfig, retryCount + 1);
                                    }
                                    return callModelWithRecovery(
                                            messages, modelConfig, retryCount + 1);
                                }));
    }

    /** Truncate oldest non-system messages, keeping system messages and the last N messages. */
    List<Msg> truncateOldest(List<Msg> messages) {
        var system = messages.stream().filter(m -> m.role() == MsgRole.SYSTEM).toList();
        var nonSystem = messages.stream().filter(m -> m.role() != MsgRole.SYSTEM).toList();
        int keepLast = Math.min(10, nonSystem.size());
        var result = new ArrayList<Msg>(system);
        result.addAll(
                nonSystem.subList(Math.max(0, nonSystem.size() - keepLast), nonSystem.size()));
        return result;
    }
}
