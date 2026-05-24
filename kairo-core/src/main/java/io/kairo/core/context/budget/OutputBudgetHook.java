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

import io.kairo.api.hook.HookHandler;
import io.kairo.api.hook.HookPhase;
import io.kairo.api.hook.HookResult;
import io.kairo.api.hook.PostReasoningEvent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-reasoning hook that drives the "auto-continue until output budget hit" loop.
 *
 * <p>On each PostReasoning event:
 *
 * <ol>
 *   <li>Pull this round's output token count from the model response usage.
 *   <li>Forward to the {@link OutputBudgetTracker}.
 *   <li>If the tracker says {@link OutputBudgetTracker.Decision.Kind#CONTINUE}, inject a
 *       continuation message that nudges the model to keep working, and note the continuation.
 *   <li>Otherwise (no budget / target reached / diminishing returns) let the agent end naturally.
 * </ol>
 *
 * <p>Wiring: instantiate one hook per session, hand it the per-session {@link OutputBudgetTracker},
 * and register on the {@link io.kairo.api.hook.HookChain}. The hook is a no-op when no budget is
 * active, so it's safe to leave registered for every session.
 *
 * @since 1.3
 */
public final class OutputBudgetHook {

    private static final Logger log = LoggerFactory.getLogger(OutputBudgetHook.class);

    /** Source tag for {@link HookResult#inject} so users can identify this hook in logs/UI. */
    public static final String HOOK_SOURCE = "OutputBudgetHook";

    private final OutputBudgetTracker tracker;

    public OutputBudgetHook(OutputBudgetTracker tracker) {
        this.tracker = tracker;
    }

    @HookHandler(HookPhase.POST_REASONING)
    public HookResult<PostReasoningEvent> onPostReasoning(PostReasoningEvent event) {
        if (event == null || event.response() == null) {
            return HookResult.proceed(event);
        }
        if (tracker.currentBudget().isEmpty()) {
            // Hot path — no budget, no work to do.
            return HookResult.proceed(event);
        }

        int outputTokens = extractOutputTokens(event.response());
        tracker.recordRound(outputTokens);

        OutputBudgetTracker.Decision decision = tracker.evaluate();
        switch (decision.kind()) {
            case CONTINUE -> {
                tracker.noteContinuation();
                Msg nudge = Msg.of(MsgRole.USER, continuationMessage(decision));
                log.debug(
                        "OutputBudgetHook nudging: {}/{} tokens ({}%)",
                        decision.tokensSoFar(),
                        decision.targetTokens(),
                        Math.round(decision.percentComplete()));
                return HookResult.inject(event, nudge, HOOK_SOURCE);
            }
            case STOP_BUDGET_REACHED ->
                    log.info(
                            "OutputBudgetHook: budget met ({}/{} tokens, {}%) — letting agent finish",
                            decision.tokensSoFar(),
                            decision.targetTokens(),
                            Math.round(decision.percentComplete()));
            case STOP_DIMINISHING_RETURNS ->
                    log.info(
                            "OutputBudgetHook: diminishing returns ({}/{} tokens, {}%) — stopping early",
                            decision.tokensSoFar(),
                            decision.targetTokens(),
                            Math.round(decision.percentComplete()));
            case NO_BUDGET -> {
                /* unreachable — we checked above */
            }
        }
        return HookResult.proceed(event);
    }

    private static int extractOutputTokens(ModelResponse response) {
        ModelResponse.Usage u = response.usage();
        return u == null ? 0 : Math.max(0, u.outputTokens());
    }

    /**
     * Wording closely tracks Claude Code so models trained on Anthropic prompts behave the same.
     */
    static String continuationMessage(OutputBudgetTracker.Decision d) {
        return String.format(
                "Output token target: %,d / %,d (%.0f%%). Keep working — produce more output to"
                        + " reach the target. The target is a hard minimum, not a suggestion. Stay"
                        + " on the user's original task; do not summarise or wrap up early.",
                d.tokensSoFar(), d.targetTokens(), d.percentComplete());
    }
}
