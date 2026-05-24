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
package io.kairo.evolution.curator;

import java.time.Duration;

/**
 * The curator must NOT consume agent context while an agent is actively reasoning (its forked
 * review eats prompt-cache real estate). Hosts hand the curator an idle signal so it can defer.
 *
 * <p>The default {@link #alwaysIdle()} suits batch/cron environments where there is no live agent
 * session. Hosts with a REPL or long-lived agent runtime should plug in an implementation backed by
 * their session manager.
 */
@FunctionalInterface
public interface CuratorIdleSignal {

    /**
     * @return true if the host has been idle for at least {@code threshold}; the curator may
     *     proceed.
     */
    boolean idleFor(Duration threshold);

    /** Signal that always reports idle — safe for batch / cron / server-side contexts. */
    static CuratorIdleSignal alwaysIdle() {
        return threshold -> true;
    }

    /** Signal that never reports idle — useful as a "pause curator" override. */
    static CuratorIdleSignal neverIdle() {
        return threshold -> false;
    }
}
