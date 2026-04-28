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
 * Pluggable callback for agent call lifecycle events.
 *
 * <p>Allows kairo-observability and other modules to collect metrics without introducing Micrometer
 * as a kairo-core dependency. Register via {@link #setGlobal(AgentCallObserver)}.
 */
public interface AgentCallObserver {

    /** Called immediately after the agent transitions to RUNNING state. */
    void onCallStart(String agentId, String agentName);

    /**
     * Called in the agent's {@code doFinally} signal handler after the call completes or fails.
     *
     * @param success {@code true} if the agent completed normally ({@code AgentState.COMPLETED})
     */
    void onCallEnd(String agentId, String agentName, Duration duration, boolean success);

    static void setGlobal(AgentCallObserver observer) {
        Holder.INSTANCE.set(observer);
    }

    static AgentCallObserver global() {
        return Holder.INSTANCE.get();
    }

    final class Holder {
        static final AtomicReference<AgentCallObserver> INSTANCE =
                new AtomicReference<>(NoopAgentCallObserver.INSTANCE);

        private Holder() {}
    }
}
