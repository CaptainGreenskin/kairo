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
import io.kairo.api.execution.DurableExecutionStore;
import javax.annotation.Nullable;

/**
 * Durable execution capability grouping the {@code DurableExecutionStore} and recovery-on-startup
 * flag. The recovery handler implementation lives in {@code kairo-core} so it cannot be referenced
 * from {@code kairo-api} directly; agents that need a custom recovery handler should provide it via
 * {@code AgentBuilder.recoveryHandler(...)}.
 *
 * @param store the durable execution store, {@code null} disables durable execution
 * @param recoveryOnStartup whether to replay the pending event log on agent construction
 * @since v0.10 (Experimental)
 */
@Experimental("AgentConfig capability — contract may change in v0.11")
public record DurableCapabilityConfig(
        @Nullable DurableExecutionStore store, boolean recoveryOnStartup) {

    /** Disabled durable execution — the default. */
    public static final DurableCapabilityConfig DISABLED = new DurableCapabilityConfig(null, false);

    /** Convenience factory for enabling durability with defaults. */
    public static DurableCapabilityConfig of(DurableExecutionStore store) {
        return new DurableCapabilityConfig(store, true);
    }
}
