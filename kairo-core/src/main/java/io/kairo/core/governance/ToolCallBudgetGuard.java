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
import io.kairo.api.hook.ToolResultEvent;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Injects a budget-warning message when total tool calls in the session exceed a threshold.
 *
 * <p>Phase: {@link HookPhase#TOOL_RESULT} — fires after every tool call result.
 */
@Experimental("Governance guard; v0.12")
public final class ToolCallBudgetGuard extends GuardHook<ToolResultEvent> {

    private final AtomicInteger callCount = new AtomicInteger(0);

    public ToolCallBudgetGuard() {
        this(60, 100, false);
    }

    public ToolCallBudgetGuard(int warnAt, int forceAt) {
        this(warnAt, forceAt, false);
    }

    public ToolCallBudgetGuard(int warnAt, int forceAt, boolean interactive) {
        super(warnAt, forceAt, interactive);
    }

    @HookHandler(HookPhase.TOOL_RESULT)
    public HookResult<ToolResultEvent> onToolResult(ToolResultEvent event) {
        callCount.incrementAndGet();
        return evaluate(event);
    }

    @Override
    protected int currentValue(ToolResultEvent event) {
        return callCount.get();
    }

    @Override
    protected String warnMessage(int value) {
        return "You have made " + value + " tool calls. Start wrapping up and commit progress.";
    }

    @Override
    protected String forceMessage(int value) {
        return "STOP. " + value + " tool calls used. Commit whatever is done now and finish.";
    }

    @Override
    protected String hookName() {
        return "ToolCallBudgetGuard";
    }

    public int callCount() {
        return callCount.get();
    }

    @Override
    public void reset() {
        super.reset();
        callCount.set(0);
    }
}
