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
 * Terminal status of a {@link TeamResult}.
 *
 * <p>Mirrors the terminal states of the expert-team lifecycle state machine (ADR-015 §"Lifecycle
 * state machine").
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Team terminal status enum; introduced in v0.10, targeting stabilization in v1.1")
public enum TeamStatus {

    /** All steps completed successfully and passed evaluation. */
    COMPLETED,

    /** The team aborted before producing a usable result. */
    FAILED,

    /** The team exceeded its {@link TeamConfig#teamTimeout()} and returned a partial result. */
    TIMEOUT,

    /**
     * Best-effort success with warnings — for example, review-loop overrun under {@link
     * RiskProfile#LOW}.
     */
    DEGRADED,

    /** The team was cancelled cooperatively before completion. */
    CANCELLED
}
