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
 * <p>If tool calls occurred in the last K iterations, the model is likely mid-task and the
 * text-only response is narration rather than a final answer. This strategy nudges the model to
 * continue making tool calls instead of pausing to explain.
 *
 * <p>Signal-based: always nudges when recent tool activity is detected. Doom-loop and runaway
 * protection are handled by {@code LoopDetector} (tool+args repetition) and the iteration/token
 * budget guards in {@link CompositeContinuationStrategy}, so this strategy intentionally has no
 * per-session nudge budget — long, legitimate tasks must not be cut off by an arbitrary cap.
 *
 * <p>Returns {@link ContinuationDecision.Pass} only when there were no recent tool calls,
 * indicating the model genuinely finished.
 *
 * @since 0.5.0
 */
public final class RecentToolActivityStrategy implements AgentContinuationStrategy {

    private final int lookbackWindow;

    private static final String NUDGE_MESSAGE =
            "You were making good progress with tool calls. Continue executing — "
                    + "do not pause to narrate. Call the next tool now.";

    /**
     * Creates a strategy with the specified lookback window.
     *
     * @param lookbackWindow number of recent iterations to check for tool activity
     */
    public RecentToolActivityStrategy(int lookbackWindow) {
        this.lookbackWindow = lookbackWindow;
    }

    /** Creates a strategy with default lookback window of 3. */
    public RecentToolActivityStrategy() {
        this(3);
    }

    @Override
    public Mono<ContinuationDecision> decide(ContinuationContext ctx) {
        if (ctx.toolCallsInLastKIterations() > 0) {
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

    @Override
    public String name() {
        return "RecentToolActivity";
    }
}
