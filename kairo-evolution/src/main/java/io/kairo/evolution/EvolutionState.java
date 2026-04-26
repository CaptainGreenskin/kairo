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
package io.kairo.evolution;

/**
 * Lifecycle states for the evolution pipeline of a single agent.
 *
 * @since v0.9 (Experimental)
 */
public enum EvolutionState {
    /** No evolution activity in progress. */
    IDLE,
    /** An evolution review is currently running. */
    REVIEWING,
    /** A candidate skill is quarantined pending content scan. */
    QUARANTINED,
    /** An evolution outcome has been applied (skill activated). */
    APPLIED,
    /** A transient failure occurred; retry is possible. */
    FAILED_RETRYABLE,
    /** A hard (non-retryable) failure occurred. */
    FAILED_HARD,
    /** Evolution is suspended due to repeated failures. */
    SUSPENDED
}
