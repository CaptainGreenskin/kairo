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
package io.kairo.core.context;

import io.kairo.api.context.TokenBudget;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.model.ModelRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages token budget tracking for context window management.
 *
 * <p>Tracks how many tokens have been used, calculates remaining capacity, and computes a pressure
 * ratio (0.0–1.0) that indicates how close the context is to its limit.
 *
 * <p>Supports API-reported usage for accurate token counting: when the last API call was in the
 * current turn, its {@code inputTokens} is used directly; otherwise a conservative character-based
 * estimate ({@code chars * 4 / 3}) is applied.
 *
 * <p>Thread-safe: uses {@link AtomicInteger} for concurrent token tracking.
 */
public class TokenBudgetManager {

    /** Buffer reserved beyond model output tokens for overhead (tool schemas, framing, etc.). */
    private static final int BUFFER = 13_000;

    private final int totalBudget;
    private final int reservedForResponse;
    private final AtomicInteger usedTokens = new AtomicInteger(0);
    private final TokenEstimator tokenEstimator;

    private final ModelRegistry.ModelSpec modelSpec;
    private volatile ModelResponse.Usage lastApiUsage;
    private volatile int lastApiUsageTurn = -1;
    private volatile int currentTurn = 0;

    /**
     * Create a new TokenBudgetManager.
     *
     * @param totalBudget the total token capacity of the model's context window
     * @param reservedForResponse tokens reserved for the model's response
     */
    public TokenBudgetManager(int totalBudget, int reservedForResponse) {
        this(totalBudget, reservedForResponse, new HeuristicTokenEstimator());
    }

    /**
     * Create a new TokenBudgetManager with a custom fallback token estimator.
     *
     * @param totalBudget the total token capacity of the model's context window
     * @param reservedForResponse tokens reserved for the model's response
     * @param tokenEstimator fallback estimator used when fresh API usage is unavailable
     */
    public TokenBudgetManager(
            int totalBudget, int reservedForResponse, TokenEstimator tokenEstimator) {
        this.totalBudget = totalBudget;
        this.reservedForResponse = reservedForResponse;
        this.modelSpec = new ModelRegistry.ModelSpec(totalBudget, reservedForResponse);
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * Create a TokenBudgetManager derived from a model ID via {@link ModelRegistry}.
     *
     * @param modelId the model identifier (e.g. "claude-sonnet-4-20250514")
     */
    public TokenBudgetManager(String modelId) {
        this(modelId, new HeuristicTokenEstimator());
    }

    /**
     * Create a TokenBudgetManager derived from a model ID with a custom fallback estimator.
     *
     * @param modelId the model identifier (e.g. "claude-sonnet-4-20250514")
     * @param tokenEstimator fallback estimator used when fresh API usage is unavailable
     */
    public TokenBudgetManager(String modelId, TokenEstimator tokenEstimator) {
        this.modelSpec = ModelRegistry.getSpec(modelId);
        this.totalBudget = modelSpec.contextWindow();
        this.reservedForResponse = modelSpec.maxOutputTokens();
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * Factory for Claude 200K context window.
     *
     * @return a TokenBudgetManager configured for 200K tokens
     */
    public static TokenBudgetManager forClaude200K() {
        return new TokenBudgetManager(200_000, 8_096);
    }

    /**
     * Factory for Claude 1M context window.
     *
     * @return a TokenBudgetManager configured for 1M tokens
     */
    public static TokenBudgetManager forClaude1M() {
        return new TokenBudgetManager(1_000_000, 16_000);
    }

    /**
     * Factory that looks up model specs from {@link ModelRegistry}.
     *
     * @param modelId the model identifier
     * @return a TokenBudgetManager configured for the given model
     */
    public static TokenBudgetManager forModel(String modelId) {
        return new TokenBudgetManager(modelId);
    }

    /**
     * Record additional token usage.
     *
     * @param tokens the number of tokens consumed
     */
    public void recordUsage(int tokens) {
        usedTokens.addAndGet(tokens);
    }

    /**
     * Reduce the used token count (e.g. after compaction).
     *
     * @param tokens the number of tokens freed
     */
    public void releaseUsage(int tokens) {
        usedTokens.addAndGet(-tokens);
    }

    /**
     * Get the current number of used tokens.
     *
     * @return the used token count
     */
    public int used() {
        return usedTokens.get();
    }

    /**
     * Get the remaining token capacity (excluding response reservation).
     *
     * @return the remaining tokens available
     */
    public int remaining() {
        return totalBudget - reservedForResponse - usedTokens.get();
    }

    /**
     * Calculate the current pressure ratio.
     *
     * <p>Pressure is the ratio of used tokens to the effective budget (total minus reserved for
     * response). Values range from 0.0 (empty) to 1.0+ (at or over capacity).
     *
     * @return the pressure ratio
     */
    public float pressure() {
        int effective = totalBudget - reservedForResponse;
        if (effective <= 0) {
            return 1.0f;
        }
        return (float) usedTokens.get() / effective;
    }

    /**
     * Get a snapshot of the current budget status.
     *
     * @return a {@link TokenBudget} snapshot
     */
    public TokenBudget getBudget() {
        return new TokenBudget(totalBudget, used(), remaining(), pressure(), reservedForResponse);
    }

    /** Reset the used token count to zero. */
    public void reset() {
        usedTokens.set(0);
    }

    // ---- API usage feedback ----

    /**
     * Update with usage reported by the API response.
     *
     * @param usage the usage from the model response
     */
    public void updateFromApiUsage(ModelResponse.Usage usage) {
        this.lastApiUsage = usage;
        this.lastApiUsageTurn = currentTurn;
    }

    /**
     * Estimate token count for a list of messages.
     *
     * <p>If the last API usage is fresh (same turn), returns the API-reported {@code inputTokens}.
     * Otherwise falls back to a conservative character-based estimate: {@code totalChars * 4 / 3}.
     *
     * @param messages the messages to estimate
     * @return estimated token count
     */
    public int estimateTokens(List<Msg> messages) {
        if (lastApiUsage != null && lastApiUsageTurn == currentTurn) {
            return lastApiUsage.inputTokens();
        }
        return tokenEstimator.estimate(messages);
    }

    /**
     * Get the effective input budget (context window minus output reservation minus buffer).
     *
     * @return the effective budget in tokens
     */
    public int getEffectiveBudget() {
        return modelSpec.contextWindow() - modelSpec.maxOutputTokens() - BUFFER;
    }

    /**
     * Get the remaining budget given a set of messages.
     *
     * @param messages the current messages
     * @return remaining tokens available
     */
    public int getRemainingBudget(List<Msg> messages) {
        return getEffectiveBudget() - estimateTokens(messages);
    }

    /**
     * Calculate context pressure for a set of messages.
     *
     * <p>Returns a value between 0.0 (empty) and 1.0+ (at or over capacity).
     *
     * @param messages the current messages
     * @return the pressure ratio
     */
    public double getPressure(List<Msg> messages) {
        int budget = getEffectiveBudget();
        if (budget <= 0) {
            return 1.0;
        }
        return (double) estimateTokens(messages) / budget;
    }

    /** Advance the turn counter (call after each model invocation). */
    public void advanceTurn() {
        currentTurn++;
    }

    /**
     * Get the model specification backing this budget manager.
     *
     * @return the model spec
     */
    public ModelRegistry.ModelSpec getModelSpec() {
        return modelSpec;
    }
}
