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
package io.kairo.core.governance;

import io.kairo.api.Experimental;
import io.kairo.api.hook.HookHandler;
import io.kairo.api.hook.HookPhase;
import io.kairo.api.hook.HookResult;
import io.kairo.api.hook.PreReasoningEvent;
import io.kairo.api.message.Msg;
import io.kairo.core.context.HeuristicTokenEstimator;
import java.util.List;

/**
 * Warns the model when conversation context grows large, to prevent context window overflow.
 *
 * <p>Estimates token count via the framework's content-type-aware estimator and injects warnings at
 * two thresholds. Active in both interactive and one-shot mode.
 *
 * <p>Phase: {@link HookPhase#PRE_REASONING} — fires before each model call.
 */
@Experimental("Governance guard; v0.12")
public final class ContextSizeGuard extends GuardHook<PreReasoningEvent> {

    private static final HeuristicTokenEstimator ESTIMATOR = new HeuristicTokenEstimator();

    private volatile int lastEstimate;

    public ContextSizeGuard() {
        this(40_000, 70_000);
    }

    public ContextSizeGuard(int warnAt, int criticalAt) {
        this(warnAt, criticalAt, false);
    }

    public ContextSizeGuard(int warnAt, int criticalAt, boolean interactive) {
        super(warnAt, criticalAt, interactive);
    }

    @Override
    protected boolean suppressInInteractive() {
        return false;
    }

    @HookHandler(HookPhase.PRE_REASONING)
    public HookResult<PreReasoningEvent> onPreReasoning(PreReasoningEvent event) {
        lastEstimate = estimateTokens(event.messages());
        return evaluate(event);
    }

    @Override
    protected int currentValue(PreReasoningEvent event) {
        return lastEstimate;
    }

    @Override
    protected String warnMessage(int value) {
        return "Context is getting large (~"
                + value
                + " tokens). Keep responses concise and avoid"
                + " repeating prior information.";
    }

    @Override
    protected String forceMessage(int value) {
        return "Context window is near capacity (~"
                + value
                + " tokens). Run /compact to compress"
                + " history, or the session may degrade.";
    }

    @Override
    protected String hookName() {
        return "ContextSizeGuard";
    }

    public int lastEstimate() {
        return lastEstimate;
    }

    int estimateTokens(List<Msg> messages) {
        return ESTIMATOR.estimate(messages);
    }
}
