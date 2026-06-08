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
import io.kairo.api.hook.PostReasoningEvent;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prevents runaway agent loops by injecting wrap-up directives when the turn count exceeds
 * configured thresholds.
 *
 * <p>Phase: {@link HookPhase#POST_REASONING} — fires after each model response.
 */
@Experimental("Governance guard; v0.12")
public final class MaxTurnsGuard extends GuardHook<PostReasoningEvent> {

    private final AtomicInteger turnCount = new AtomicInteger(0);

    public MaxTurnsGuard() {
        this(20, 30, false);
    }

    public MaxTurnsGuard(int warnAt, int forceAt) {
        this(warnAt, forceAt, false);
    }

    public MaxTurnsGuard(int warnAt, int forceAt, boolean interactive) {
        super(warnAt, forceAt, interactive);
    }

    @HookHandler(HookPhase.POST_REASONING)
    public HookResult<PostReasoningEvent> onPostReasoning(PostReasoningEvent event) {
        turnCount.incrementAndGet();
        return evaluate(event);
    }

    @Override
    protected int currentValue(PostReasoningEvent event) {
        return turnCount.get();
    }

    @Override
    protected String warnMessage(int value) {
        return "You have used "
                + value
                + " turns. Start wrapping up: commit any completed changes"
                + " and stop planning new work.";
    }

    @Override
    protected String forceMessage(int value) {
        return "STOP. You have used "
                + value
                + " turns — maximum allowed. Commit whatever is"
                + " done now and finish.";
    }

    @Override
    protected String hookName() {
        return "MaxTurnsGuard";
    }

    public int turnCount() {
        return turnCount.get();
    }

    @Override
    public void reset() {
        super.reset();
        turnCount.set(0);
    }
}
