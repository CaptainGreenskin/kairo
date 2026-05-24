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

import io.kairo.api.hook.HookResult;
import io.kairo.api.hook.PostReasoningEvent;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutputBudgetHookTest {

    private static PostReasoningEvent eventWith(int outputTokens) {
        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent("(model output omitted)")),
                        new ModelResponse.Usage(0, outputTokens, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");
        return new PostReasoningEvent(response, false);
    }

    @Test
    void onPostReasoning_withoutBudget_returnsContinue() {
        OutputBudgetTracker tracker = new OutputBudgetTracker();
        OutputBudgetHook hook = new OutputBudgetHook(tracker);

        HookResult<PostReasoningEvent> r = hook.onPostReasoning(eventWith(5_000));
        assertThat(r.decision()).isEqualTo(HookResult.Decision.CONTINUE);
        assertThat(r.injectedMessage()).isNull();
    }

    @Test
    void onPostReasoning_underBudget_injectsContinuation() {
        OutputBudgetTracker tracker = new OutputBudgetTracker();
        tracker.startTurn(OutputBudget.ofMega(1)); // 1M target
        OutputBudgetHook hook = new OutputBudgetHook(tracker);

        HookResult<PostReasoningEvent> r = hook.onPostReasoning(eventWith(100_000));

        assertThat(r.decision()).isEqualTo(HookResult.Decision.INJECT);
        assertThat(r.injectedMessage()).isNotNull();
        assertThat(r.injectedMessage().text())
                .contains("Output token target")
                .contains("100,000")
                .contains("1,000,000");
        assertThat(r.hookSource()).isEqualTo(OutputBudgetHook.HOOK_SOURCE);
        assertThat(tracker.continuationCount()).isOne();
    }

    @Test
    void onPostReasoning_budgetReached_doesNotInject_andLetsAgentFinish() {
        OutputBudgetTracker tracker = new OutputBudgetTracker();
        tracker.startTurn(OutputBudget.ofKilo(100)); // 100k target → 90k threshold
        OutputBudgetHook hook = new OutputBudgetHook(tracker);

        HookResult<PostReasoningEvent> r = hook.onPostReasoning(eventWith(95_000));
        assertThat(r.decision()).isEqualTo(HookResult.Decision.CONTINUE);
        assertThat(r.injectedMessage()).isNull();
    }

    @Test
    void onPostReasoning_diminishingReturns_stopsInjecting() {
        OutputBudgetTracker tracker = new OutputBudgetTracker();
        tracker.startTurn(OutputBudget.ofMega(10));
        OutputBudgetHook hook = new OutputBudgetHook(tracker);

        // The guard requires continuationCount >= 3 (DIMINISHING_WINDOW) AND the recent N=3
        // deltas all below DIMINISHING_TOKEN_FLOOR (500). The hook calls noteContinuation()
        // AFTER deciding to INJECT, so the count only reaches 3 by the start of round 4. Hence
        // rounds 1-3 all INJECT and round 4 is where we expect the agent to be allowed to stop.
        HookResult<PostReasoningEvent> r1 = hook.onPostReasoning(eventWith(50));
        HookResult<PostReasoningEvent> r2 = hook.onPostReasoning(eventWith(50));
        HookResult<PostReasoningEvent> r3 = hook.onPostReasoning(eventWith(50));
        HookResult<PostReasoningEvent> r4 = hook.onPostReasoning(eventWith(50));

        assertThat(r1.decision()).isEqualTo(HookResult.Decision.INJECT);
        assertThat(r2.decision()).isEqualTo(HookResult.Decision.INJECT);
        assertThat(r3.decision()).isEqualTo(HookResult.Decision.INJECT);
        assertThat(r4.decision())
                .as("After 3 weak nudges the diminishing-returns guard fires on the 4th round")
                .isEqualTo(HookResult.Decision.CONTINUE);
        assertThat(r4.injectedMessage()).isNull();
    }

    @Test
    void onPostReasoning_nullEvent_safelyContinues() {
        OutputBudgetTracker tracker = new OutputBudgetTracker();
        tracker.startTurn(OutputBudget.ofKilo(100));
        OutputBudgetHook hook = new OutputBudgetHook(tracker);

        HookResult<PostReasoningEvent> r = hook.onPostReasoning(null);
        assertThat(r.decision()).isEqualTo(HookResult.Decision.CONTINUE);
    }

    @Test
    void onPostReasoning_nullResponse_safelyContinues() {
        OutputBudgetTracker tracker = new OutputBudgetTracker();
        tracker.startTurn(OutputBudget.ofKilo(100));
        OutputBudgetHook hook = new OutputBudgetHook(tracker);

        PostReasoningEvent event = new PostReasoningEvent(null, false);
        HookResult<PostReasoningEvent> r = hook.onPostReasoning(event);
        assertThat(r.decision()).isEqualTo(HookResult.Decision.CONTINUE);
    }

    @Test
    void onPostReasoning_nullUsage_treatedAsZeroTokens() {
        // Some providers don't surface usage on every chunk. Don't NaN, don't NPE — count as 0.
        ModelResponse response =
                new ModelResponse(
                        "r",
                        List.of(new Content.TextContent("...")),
                        null,
                        ModelResponse.StopReason.END_TURN,
                        "test-model");
        PostReasoningEvent event = new PostReasoningEvent(response, false);

        OutputBudgetTracker tracker = new OutputBudgetTracker();
        tracker.startTurn(OutputBudget.ofKilo(100));
        OutputBudgetHook hook = new OutputBudgetHook(tracker);

        HookResult<PostReasoningEvent> r = hook.onPostReasoning(event);
        // tokensSoFar stayed at 0 → still well under threshold → INJECT.
        assertThat(r.decision()).isEqualTo(HookResult.Decision.INJECT);
        assertThat(tracker.tokensThisTurn()).isZero();
    }

    @Test
    void continuationMessage_includesTargetAndPercent() {
        OutputBudgetTracker.Decision d =
                new OutputBudgetTracker.Decision(
                        OutputBudgetTracker.Decision.Kind.CONTINUE, 100_000, 1_000_000, 10.0);
        String msg = OutputBudgetHook.continuationMessage(d);
        assertThat(msg)
                .contains("100,000")
                .contains("1,000,000")
                .contains("10%")
                .contains("hard minimum");
    }
}
