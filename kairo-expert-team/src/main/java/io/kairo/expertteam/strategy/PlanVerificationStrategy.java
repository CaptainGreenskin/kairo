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
package io.kairo.expertteam.strategy;

import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamResult;

/**
 * Strategy for verifying that a plan execution achieved its intended goal. Runs after all steps
 * complete and before the final result is returned.
 */
public interface PlanVerificationStrategy {

    /**
     * Verify the result of executing a plan.
     *
     * @param plan the original execution plan
     * @param result the execution result with step outcomes
     * @param originalGoal the user's original goal text
     * @return verification verdict
     */
    PlanVerificationVerdict verify(TeamExecutionPlan plan, TeamResult result, String originalGoal);
}
