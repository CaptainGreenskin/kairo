/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.context.budget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutputBudgetTrackerTest {

    @Test
    void evaluate_withoutBudget_returnsNoBudget() {
        OutputBudgetTracker t = new OutputBudgetTracker();
        OutputBudgetTracker.Decision d = t.evaluate();
        assertThat(d.kind()).isEqualTo(OutputBudgetTracker.Decision.Kind.NO_BUDGET);
        assertThat(t.currentBudget()).isEmpty();
    }

    @Test
    void startTurn_resetsAccumulators() {
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofKilo(500));
        t.recordRound(100_000);
        t.noteContinuation();

        // New turn — state must wipe so leftover counts don't trip diminishing returns.
        t.startTurn(OutputBudget.ofKilo(200));
        assertThat(t.tokensThisTurn()).isZero();
        assertThat(t.continuationCount()).isZero();
        assertThat(t.currentBudget()).map(OutputBudget::totalTokens).contains(200_000L);
    }

    @Test
    void evaluate_underThreshold_continues() {
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofKilo(1000)); // 1,000,000 tokens
        t.recordRound(100_000); // 10%

        OutputBudgetTracker.Decision d = t.evaluate();
        assertThat(d.kind()).isEqualTo(OutputBudgetTracker.Decision.Kind.CONTINUE);
        assertThat(d.tokensSoFar()).isEqualTo(100_000);
        assertThat(d.targetTokens()).isEqualTo(1_000_000);
        assertThat(d.percentComplete()).isEqualTo(10.0);
    }

    @Test
    void evaluate_atCompletionThreshold_stops() {
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofKilo(1000)); // budget = 1,000,000; threshold = 900,000
        t.recordRound(900_000);

        OutputBudgetTracker.Decision d = t.evaluate();
        assertThat(d.kind()).isEqualTo(OutputBudgetTracker.Decision.Kind.STOP_BUDGET_REACHED);
    }

    @Test
    void evaluate_overBudget_stops_andPercentCappedAt100() {
        // Models can overshoot — make sure the reported percent doesn't render as e.g. 142%.
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofKilo(100));
        t.recordRound(150_000);

        OutputBudgetTracker.Decision d = t.evaluate();
        assertThat(d.kind()).isEqualTo(OutputBudgetTracker.Decision.Kind.STOP_BUDGET_REACHED);
        assertThat(d.percentComplete()).isEqualTo(100.0);
    }

    @Test
    void evaluate_diminishingReturns_stopsAfterWindow() {
        // 3 nudges in a row, all under the 500-token floor → guard fires.
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofMega(10));

        for (int i = 0; i < 3; i++) {
            t.recordRound(100); // each well below DIMINISHING_TOKEN_FLOOR
            t.noteContinuation();
        }

        OutputBudgetTracker.Decision d = t.evaluate();
        assertThat(d.kind()).isEqualTo(OutputBudgetTracker.Decision.Kind.STOP_DIMINISHING_RETURNS);
    }

    @Test
    void evaluate_oneStrongRoundInWindow_doesNotTriggerDiminishingReturns() {
        // If any round in the window is above the floor, the guard must NOT fire.
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofMega(10));

        t.recordRound(100);
        t.noteContinuation();
        t.recordRound(50_000); // strong round breaks the stall pattern
        t.noteContinuation();
        t.recordRound(200);
        t.noteContinuation();

        OutputBudgetTracker.Decision d = t.evaluate();
        assertThat(d.kind()).isEqualTo(OutputBudgetTracker.Decision.Kind.CONTINUE);
    }

    @Test
    void evaluate_diminishing_onlyAfterRequiredWindow() {
        // 2 weak rounds shouldn't be enough — the guard only fires AFTER the full window.
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofMega(10));

        t.recordRound(50);
        t.noteContinuation();
        t.recordRound(50);
        t.noteContinuation();

        OutputBudgetTracker.Decision d = t.evaluate();
        assertThat(d.kind()).isEqualTo(OutputBudgetTracker.Decision.Kind.CONTINUE);
    }

    @Test
    void recordRound_negativeTreatedAsZero() {
        // Provider quirks sometimes report negative usage; tracker should ignore, not double-count.
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofKilo(100));
        t.recordRound(-50);
        assertThat(t.tokensThisTurn()).isZero();
    }

    @Test
    void endTurn_clearsBudget_andEvaluateReturnsNoBudget() {
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofKilo(100));
        t.recordRound(10_000);
        t.endTurn();
        assertThat(t.currentBudget()).isEmpty();
        assertThat(t.tokensThisTurn()).isZero();
        assertThat(t.evaluate().kind()).isEqualTo(OutputBudgetTracker.Decision.Kind.NO_BUDGET);
    }

    @Test
    void continuationCount_incrementsExplicitly() {
        // noteContinuation is a manual step (the hook calls it when it actually injects a nudge);
        // recordRound alone must NOT advance the counter.
        OutputBudgetTracker t = new OutputBudgetTracker();
        t.startTurn(OutputBudget.ofKilo(100));
        t.recordRound(5_000);
        assertThat(t.continuationCount()).isZero();
        t.noteContinuation();
        assertThat(t.continuationCount()).isOne();
    }
}
