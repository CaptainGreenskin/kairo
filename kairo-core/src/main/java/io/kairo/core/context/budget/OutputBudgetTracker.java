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
package io.kairo.core.context.budget;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Tracks per-turn progress against a user-declared {@link OutputBudget} and decides whether the
 * agent should be nudged to keep going or allowed to stop.
 *
 * <p>One instance per session (REPL or SDK). Lifecycle:
 *
 * <ol>
 *   <li>{@link #startTurn(OutputBudget)} when the user submits a prompt that contains a budget.
 *   <li>{@link #recordRound(int)} on each post-reasoning event with that round's output tokens.
 *   <li>{@link #evaluate()} returns the {@link Decision} for the just-recorded round.
 *   <li>{@link #endTurn()} when the model declares done OR the budget is reached OR the user
 *       interrupts — clears state so the next turn starts fresh.
 * </ol>
 *
 * <p>Decision rules (mirrors Claude Code's {@code checkTokenBudget}):
 *
 * <ul>
 *   <li>{@link Decision.Kind#CONTINUE} when total tokens this turn &lt; {@link
 *       #COMPLETION_THRESHOLD} of the budget AND no diminishing-returns guard fired.
 *   <li>{@link Decision.Kind#STOP_BUDGET_REACHED} when total &ge; {@code COMPLETION_THRESHOLD} of
 *       budget.
 *   <li>{@link Decision.Kind#STOP_DIMINISHING_RETURNS} after {@link #DIMINISHING_WINDOW}
 *       continuations the recent rounds each added less than {@link #DIMINISHING_TOKEN_FLOOR}
 *       tokens — the model is spinning.
 *   <li>{@link Decision.Kind#NO_BUDGET} when no budget is currently set (lets the caller skip all
 *       nudging cheaply).
 * </ul>
 *
 * <p>Thread-safety: synchronised; the REPL is single-threaded but the hook may run on a reactor
 * worker. Methods are intentionally short — contention is rare and the simpler model beats a
 * fine-grained lock here.
 *
 * @since 1.3
 */
public final class OutputBudgetTracker {

    /** Stop nudging once we've produced this fraction of the budget. */
    public static final double COMPLETION_THRESHOLD = 0.9;

    /** A round adding fewer than this many tokens counts as "no progress" for the guard. */
    public static final long DIMINISHING_TOKEN_FLOOR = 500;

    /** Number of consecutive low-yield continuations before we give up. */
    public static final int DIMINISHING_WINDOW = 3;

    private OutputBudget budget;
    private long tokensThisTurn;
    private int continuationCount;
    private final Deque<Long> recentDeltas = new ArrayDeque<>();

    /** Snapshot the start of a new user turn. Replaces any in-flight budget. */
    public synchronized void startTurn(OutputBudget budget) {
        this.budget = budget;
        this.tokensThisTurn = 0;
        this.continuationCount = 0;
        this.recentDeltas.clear();
    }

    /** Clear all state. Use at user-cancel or when the turn closes without a budget hit. */
    public synchronized void endTurn() {
        this.budget = null;
        this.tokensThisTurn = 0;
        this.continuationCount = 0;
        this.recentDeltas.clear();
    }

    /** Record the output tokens produced by the most recent model round. */
    public synchronized void recordRound(int outputTokens) {
        if (outputTokens <= 0) {
            outputTokens = 0;
        }
        this.tokensThisTurn += outputTokens;
        this.recentDeltas.addLast((long) outputTokens);
        // Keep only the most recent window of deltas for the diminishing-returns check.
        while (recentDeltas.size() > DIMINISHING_WINDOW) {
            recentDeltas.removeFirst();
        }
    }

    /** Increment the continuation counter — call when a nudge is actually injected. */
    public synchronized void noteContinuation() {
        this.continuationCount++;
    }

    /** Currently set budget, if any. */
    public synchronized Optional<OutputBudget> currentBudget() {
        return Optional.ofNullable(budget);
    }

    /** Cumulative output tokens recorded this turn. */
    public synchronized long tokensThisTurn() {
        return tokensThisTurn;
    }

    /** Number of times {@link #noteContinuation()} has been called this turn. */
    public synchronized int continuationCount() {
        return continuationCount;
    }

    /**
     * Decide whether to keep nudging. Caller should treat {@link Decision.Kind#CONTINUE} as "inject
     * a continuation message and run another reasoning turn", and anything else as terminal for the
     * budget loop.
     */
    public synchronized Decision evaluate() {
        if (budget == null) {
            return new Decision(Decision.Kind.NO_BUDGET, 0L, 0L, 0.0);
        }
        long target = budget.totalTokens();
        long completionPoint = (long) (target * COMPLETION_THRESHOLD);
        double percent = target > 0 ? (100.0 * tokensThisTurn) / target : 100.0;

        if (tokensThisTurn >= completionPoint) {
            return new Decision(
                    Decision.Kind.STOP_BUDGET_REACHED,
                    tokensThisTurn,
                    target,
                    Math.min(percent, 100.0));
        }
        if (isDiminishingReturns()) {
            return new Decision(
                    Decision.Kind.STOP_DIMINISHING_RETURNS, tokensThisTurn, target, percent);
        }
        return new Decision(Decision.Kind.CONTINUE, tokensThisTurn, target, percent);
    }

    /**
     * True when the agent has already been nudged at least {@link #DIMINISHING_WINDOW} times and
     * each recent round added fewer than {@link #DIMINISHING_TOKEN_FLOOR} tokens. This catches
     * "model has nothing more to add" cases that would otherwise cycle forever.
     */
    private boolean isDiminishingReturns() {
        if (continuationCount < DIMINISHING_WINDOW) return false;
        if (recentDeltas.size() < DIMINISHING_WINDOW) return false;
        for (Long delta : recentDeltas) {
            if (delta >= DIMINISHING_TOKEN_FLOOR) {
                return false;
            }
        }
        return true;
    }

    /**
     * Outcome of {@link #evaluate()}. {@code tokensSoFar} / {@code targetTokens} / {@code
     * percentComplete} are always populated even when {@link Kind#NO_BUDGET} so callers can render
     * a uniform progress line.
     */
    public record Decision(Kind kind, long tokensSoFar, long targetTokens, double percentComplete) {

        public enum Kind {
            /** No budget active — caller can skip all nudging. */
            NO_BUDGET,
            /** Budget set, &lt; 90% used, no stall — inject a continuation message. */
            CONTINUE,
            /** Budget met (&ge; 90%) — allow natural stop. */
            STOP_BUDGET_REACHED,
            /** Model has stopped producing meaningful output — give up cleanly. */
            STOP_DIMINISHING_RETURNS
        }
    }
}
