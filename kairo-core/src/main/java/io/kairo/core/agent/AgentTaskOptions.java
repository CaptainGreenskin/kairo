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
package io.kairo.core.agent;

import java.time.Duration;
import java.util.Objects;

/**
 * Options controlling the lifecycle of an agent task submitted via {@link AgentTaskScheduler}.
 *
 * @param maxDuration maximum wall-clock time before the task is forcibly cancelled (default 30 min)
 * @param onTimeout optional callback invoked on the scheduler thread when the timeout fires; {@code
 *     null} means no callback
 */
public record AgentTaskOptions(Duration maxDuration, Runnable onTimeout) {

    private static final Duration DEFAULT_MAX_DURATION = Duration.ofMinutes(30);

    public AgentTaskOptions {
        Objects.requireNonNull(maxDuration, "maxDuration");
        if (maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("maxDuration must be positive");
        }
    }

    /** Default options: 30-minute timeout, no callback. */
    public static AgentTaskOptions defaults() {
        return new AgentTaskOptions(DEFAULT_MAX_DURATION, null);
    }

    /** Options with a custom duration and no callback. */
    public static AgentTaskOptions withTimeout(Duration maxDuration) {
        return new AgentTaskOptions(maxDuration, null);
    }

    /** Options with a custom duration and a callback invoked on timeout. */
    public static AgentTaskOptions withTimeout(Duration maxDuration, Runnable onTimeout) {
        return new AgentTaskOptions(maxDuration, onTimeout);
    }
}
