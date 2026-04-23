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
package io.kairo.api.agent;

import io.kairo.api.Experimental;
import java.time.Duration;

/**
 * Loop detection thresholds for the agent runtime. Groups together the 5 parameters that used to
 * live as individual fields on {@code AgentConfig}.
 *
 * @param hashWarnThreshold consecutive identical hash count to trigger WARN (default 3)
 * @param hashHardLimit consecutive identical hash count to trigger HARD_STOP (default 5)
 * @param freqWarnThreshold per-tool call count within window to trigger WARN (default 50)
 * @param freqHardLimit per-tool call count within window to trigger HARD_STOP (default 100)
 * @param freqWindow sliding time window for frequency detection (default 10 minutes)
 * @since v0.10 (Experimental)
 */
@Experimental("AgentConfig capability — contract may change in v0.11")
public record LoopDetectionConfig(
        int hashWarnThreshold,
        int hashHardLimit,
        int freqWarnThreshold,
        int freqHardLimit,
        Duration freqWindow) {

    /** Sensible defaults aligned with {@link io.kairo.api.agent.AgentConfig} history. */
    public static final LoopDetectionConfig DEFAULTS =
            new LoopDetectionConfig(3, 5, 50, 100, Duration.ofMinutes(10));

    public LoopDetectionConfig {
        if (freqWindow == null) {
            freqWindow = Duration.ofMinutes(10);
        }
    }
}
