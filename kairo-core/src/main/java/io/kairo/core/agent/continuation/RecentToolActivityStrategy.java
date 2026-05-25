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
package io.kairo.core.agent.continuation;

import io.kairo.api.message.Msg;
import reactor.core.publisher.Mono;

/**
 * Catches models that "pause to narrate" mid-execution.
 *
 * <p>If tool calls occurred in the last K iterations <strong>and</strong> the assistant message
 * looks like mid-task narration (short text, currently &lt; {@link #DEFAULT_FINAL_ANSWER_MIN_CHARS}
 * chars), the model is likely pausing to explain and this strategy nudges it to continue.
 *
 * <p>Signal-based: nudges only when recent tool activity is detected AND the latest assistant text
 * is short enough to plausibly be narration. Doom-loop and runaway protection are handled by {@code
 * LoopDetector} (tool+args repetition) and the iteration/token budget guards in {@link
 * CompositeContinuationStrategy}, so this strategy intentionally has no per-session nudge budget —
 * long, legitimate tasks must not be cut off by an arbitrary cap.
 *
 * <p>Returns {@link ContinuationDecision.Pass} when there were no recent tool calls, or when the
 * assistant message is long enough to plausibly be a final answer to the user.
 *
 * @since 0.5.0
 */
public final class RecentToolActivityStrategy implements AgentContinuationStrategy {

    private final int lookbackWindow;
    private final int finalAnswerMinChars;

    private static final String NUDGE_MESSAGE =
            "You were making good progress with tool calls. Continue executing — "
                    + "do not pause to narrate. Call the next tool now.";

    /**
     * Default text length threshold above which an assistant message is treated as a final answer
     * rather than mid-task narration. Narration in practice is short ("Let me check...", ~17
     * chars); final answers tend to be structured markdown well above 200. Picked to avoid
     * false-positive nudges that re-prompt the model after it has actually answered the user.
     */
    public static final int DEFAULT_FINAL_ANSWER_MIN_CHARS = 200;

    /**
     * Creates a strategy with the specified lookback window and default final-answer threshold.
     *
     * @param lookbackWindow number of recent iterations to check for tool activity
     */
    public RecentToolActivityStrategy(int lookbackWindow) {
        this(lookbackWindow, DEFAULT_FINAL_ANSWER_MIN_CHARS);
    }

    /**
     * Creates a strategy with custom lookback window and final-answer threshold.
     *
     * @param lookbackWindow number of recent iterations to check for tool activity
     * @param finalAnswerMinChars text length at/above which a message is treated as a final answer
     */
    public RecentToolActivityStrategy(int lookbackWindow, int finalAnswerMinChars) {
        this.lookbackWindow = lookbackWindow;
        this.finalAnswerMinChars = finalAnswerMinChars;
    }

    /** Creates a strategy with default lookback window of 3. */
    public RecentToolActivityStrategy() {
        this(3);
    }

    @Override
    public Mono<ContinuationDecision> decide(ContinuationContext ctx) {
        if (ctx.toolCallsInLastKIterations() > 0 && !looksLikeFinalAnswer(ctx.lastAssistantMsg())) {
            Msg synthetic = Msg.nudge(NUDGE_MESSAGE, name());
            return Mono.just(
                    new ContinuationDecision.Nudge(
                            synthetic,
                            "recent_tools="
                                    + ctx.toolCallsInLastKIterations()
                                    + "_in_last_"
                                    + lookbackWindow));
        }

        return Mono.just(ContinuationDecision.Pass.INSTANCE);
    }

    private boolean looksLikeFinalAnswer(Msg lastAssistantMsg) {
        if (lastAssistantMsg == null) {
            return false;
        }
        String text = lastAssistantMsg.text();
        return text != null && text.length() >= finalAnswerMinChars;
    }

    @Override
    public String name() {
        return "RecentToolActivity";
    }
}
