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
import io.kairo.api.hook.HookResult;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;

/**
 * Abstract base for dual-threshold governance hooks that inject warn/force messages into the
 * conversation when a monitored metric exceeds configured thresholds.
 *
 * <p>Semantics:
 *
 * <ul>
 *   <li>Each threshold fires exactly once per session (fire-once)
 *   <li>Force threshold takes priority over warn (if both cross simultaneously)
 *   <li>Interactive mode can be excluded via {@link #suppressInInteractive()}
 * </ul>
 *
 * @param <E> the hook event type this guard monitors
 */
@Experimental("Governance hook framework; v0.12")
public abstract class GuardHook<E> {

    private final int warnThreshold;
    private final int forceThreshold;
    private final boolean interactive;
    private boolean warnFired;
    private boolean forceFired;

    protected GuardHook(int warnThreshold, int forceThreshold, boolean interactive) {
        this.warnThreshold = warnThreshold;
        this.forceThreshold = forceThreshold;
        this.interactive = interactive;
    }

    protected abstract int currentValue(E event);

    protected abstract String warnMessage(int value);

    protected abstract String forceMessage(int value);

    protected abstract String hookName();

    protected boolean suppressInInteractive() {
        return true;
    }

    protected final HookResult<E> evaluate(E event) {
        if (interactive && suppressInInteractive()) {
            return HookResult.proceed(event);
        }

        int value = currentValue(event);

        if (forceThreshold > 0 && value >= forceThreshold && !forceFired) {
            forceFired = true;
            warnFired = true;
            return HookResult.inject(event, Msg.of(MsgRole.USER, forceMessage(value)), hookName());
        }

        if (warnThreshold > 0 && value >= warnThreshold && !warnFired) {
            warnFired = true;
            return HookResult.inject(event, Msg.of(MsgRole.USER, warnMessage(value)), hookName());
        }

        return HookResult.proceed(event);
    }

    public void reset() {
        warnFired = false;
        forceFired = false;
    }

    public boolean isWarnFired() {
        return warnFired;
    }

    public boolean isForceFired() {
        return forceFired;
    }

    public int warnThreshold() {
        return warnThreshold;
    }

    public int forceThreshold() {
        return forceThreshold;
    }
}
