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
package io.kairo.multiagent.orchestration;

import java.time.Instant;
import java.util.List;

/**
 * A learned team-collaboration pattern (Level-2 self-evolution): which expert roles, in what
 * sequence and DAG shape, were used for a task — and whether the team succeeded. Recorded at team
 * completion and recalled at planning time so the planner can reuse compositions that worked for
 * similar tasks (and the team gets better at composing experts over time).
 *
 * @param goal the original task goal (used for similarity matching at recall)
 * @param roleSequence the roleIds in execution order (e.g. ["expert:researcher", "expert:coder"])
 * @param dagShape a short shape descriptor, e.g. "serial" or "parallel:3"
 * @param success whether the team reached a successful terminal state
 * @param score effectiveness in [0,1] (1.0 = clean success)
 * @param recordedAt when this pattern was captured
 * @since v0.11 (Experimental)
 */
public record TeamPattern(
        String goal,
        List<String> roleSequence,
        String dagShape,
        boolean success,
        double score,
        Instant recordedAt) {}
