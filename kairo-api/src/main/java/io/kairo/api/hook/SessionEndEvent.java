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
package io.kairo.api.hook;

import io.kairo.api.agent.AgentState;
import java.time.Duration;

/**
 * Event fired when an Agent session completes (success or error).
 *
 * @param agentName the name of the agent
 * @param finalState the final agent state (COMPLETED or FAILED)
 * @param iterations the number of iterations executed
 * @param tokensUsed the total tokens consumed
 * @param duration the total session duration
 * @param error the error message, or null on success
 */
public record SessionEndEvent(
        String agentName,
        AgentState finalState,
        int iterations,
        long tokensUsed,
        Duration duration,
        String error) {}
