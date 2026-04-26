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
package io.kairo.api.execution;

import io.kairo.api.Stable;
import java.time.Duration;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Immutable snapshot of the current execution state, provided to {@link ResourceConstraint}
 * implementations for validation.
 *
 * @param iteration current iteration index (0-based)
 * @param tokensUsed cumulative token consumption
 * @param elapsed wall-clock time since execution start
 * @param agentName the name of the agent being constrained
 * @param agentState optional agent state snapshot (may be null)
 */
@Stable(value = "Resource context snapshot; shape frozen since v0.8", since = "1.0.0")
public record ResourceContext(
        int iteration,
        long tokensUsed,
        Duration elapsed,
        String agentName,
        @Nullable Object agentState) {

    public ResourceContext {
        Objects.requireNonNull(elapsed, "elapsed must not be null");
        Objects.requireNonNull(agentName, "agentName must not be null");
    }
}
