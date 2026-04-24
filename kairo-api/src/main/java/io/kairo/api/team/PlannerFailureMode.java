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
 * How the coordinator reacts when the planner cannot produce a usable {@link TeamExecutionPlan}.
 *
 * <p>Per ADR-015 §"Failure semantics", the default is {@link #FAIL_FAST}.
 *
 * @since v0.10 (Experimental)
 */
@Experimental(
        "Team planner failure mode enum; introduced in v0.10, targeting stabilization in v1.1")
public enum PlannerFailureMode {

    /** Abort immediately with {@link TeamStatus#FAILED}. This is the default. */
    FAIL_FAST,

    /**
     * Opt-in resilience: fall back to a single-step plan built from the raw request goal so the
     * team can still produce best-effort output.
     */
    SINGLE_STEP_FALLBACK
}
