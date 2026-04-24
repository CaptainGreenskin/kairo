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
package io.kairo.api.team;

import io.kairo.api.Experimental;

/**
 * Caller hint for selecting an {@link EvaluationStrategy} implementation.
 *
 * @since v0.10 (Experimental)
 */
@Experimental(
        "Team evaluator preference enum; introduced in v0.10, targeting stabilization in v1.1")
public enum EvaluatorPreference {

    /** Force the deterministic rubric-based (simple) evaluator. */
    SIMPLE,

    /** Force the LLM-judge (agent-based) evaluator. */
    AGENT,

    /**
     * Delegate the choice to the coordinator, which picks an evaluator based on {@link
     * TeamConfig#riskProfile()} and runtime availability.
     */
    AUTO
}
