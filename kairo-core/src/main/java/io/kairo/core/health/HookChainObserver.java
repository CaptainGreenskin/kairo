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
package io.kairo.core.health;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pluggable callback for hook chain firing events.
 *
 * <p>Mirrors {@link AgentCallObserver} so {@code kairo-observability} can collect Micrometer
 * metrics (counters, timers) for in-process and external hook activity without making {@code
 * kairo-core} depend on Micrometer.
 *
 * <p>Register via {@link #setGlobal(HookChainObserver)}. All callbacks are best-effort —
 * implementations MUST NOT throw; if a callback raises, the hook chain logs at {@code DEBUG} and
 * continues so business semantics are unaffected.
 */
public interface HookChainObserver {

    /**
     * Called when an in-process hook handler completes (either successfully or with a non-throwing
     * decision such as {@code ABORT} / {@code SKIP}).
     *
     * @param phase the hook phase (e.g. {@code "PRE_REASONING"})
     * @param decision the decision outcome (e.g. {@code "CONTINUE"}, {@code "ABORT"}, {@code
     *     "MODIFY"}, {@code "SKIP"}, {@code "INJECT"})
     * @param duration wall-clock duration of the hook invocation
     */
    void onHookFired(String phase, String decision, Duration duration);

    /**
     * Called when an in-process hook handler throws.
     *
     * @param phase the hook phase
     * @param error the throwable raised by the handler
     * @param duration wall-clock duration up to the failure
     */
    void onHookFailed(String phase, Throwable error, Duration duration);

    /**
     * Called when an external hook (HTTP / Command) execution fails. The hook chain swallows the
     * failure to {@code CONTINUE} (so business semantics keep working), but observability backends
     * still need visibility.
     *
     * @param phase the hook phase
     * @param hookId an identifier for the failing hook (e.g. {@code "command:./guard.sh"} or {@code
     *     "http:https://hook.example.com/pre"})
     * @param error the throwable that caused the failure
     */
    void onExternalHookFailure(String phase, String hookId, Throwable error);

    static void setGlobal(HookChainObserver observer) {
        Holder.INSTANCE.set(observer == null ? NoopHookChainObserver.INSTANCE : observer);
    }

    static HookChainObserver global() {
        return Holder.INSTANCE.get();
    }

    final class Holder {
        static final AtomicReference<HookChainObserver> INSTANCE =
                new AtomicReference<>(NoopHookChainObserver.INSTANCE);

        private Holder() {}
    }
}
