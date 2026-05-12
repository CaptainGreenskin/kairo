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
import io.kairo.api.model.ModelResponse;
import reactor.core.publisher.Mono;

/**
 * Recovers from {@link ModelResponse.StopReason#MAX_TOKENS} by nudging the model to resume.
 *
 * <p>Mirrors Claude Code's {@code max_output_tokens} handler — when the model is cut off
 * mid-response, this strategy injects a continuation prompt that tells the model to resume without
 * repeating prior output.
 *
 * <p>After exhausting the retry budget, terminates with reason {@code "length_exhausted"}.
 *
 * @since 0.5.0
 */
public final class FinishReasonRecoveryStrategy implements AgentContinuationStrategy {

    private final int maxRetries;

    private static final String RESUME_MESSAGE =
            "Your previous response was cut off due to length limits. "
                    + "Resume directly from where you left off. Do not repeat what was already said. "
                    + "Continue with the next tool call.";

    /**
     * Creates a strategy with the specified retry budget.
     *
     * @param maxRetries maximum number of length-recovery nudges per session
     */
    public FinishReasonRecoveryStrategy(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /** Creates a strategy with the default retry budget of 3. */
    public FinishReasonRecoveryStrategy() {
        this(3);
    }

    @Override
    public Mono<ContinuationDecision> decide(ContinuationContext ctx) {
        if (ctx.stopReason() != ModelResponse.StopReason.MAX_TOKENS) {
            return Mono.just(ContinuationDecision.Pass.INSTANCE);
        }

        // Count how many times we've already retried for length in this session
        if (!ctx.withinNudgeBudget(maxRetries)) {
            return Mono.just(new ContinuationDecision.Terminate("length_exhausted"));
        }

        Msg synthetic = Msg.nudge(RESUME_MESSAGE, name());
        return Mono.just(new ContinuationDecision.Nudge(synthetic, "stop_reason=max_tokens"));
    }

    @Override
    public String name() {
        return "FinishReasonRecovery";
    }
}
