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
 * Signals that drive transitions in the {@link EvolutionStateMachine}.
 *
 * @since v0.9 (Experimental)
 */
public enum EvolutionSignal {
    /** Begin a new evolution review. */
    START_REVIEW,
    /** The review completed successfully. */
    REVIEW_COMPLETE,
    /** Place a candidate skill in quarantine. */
    QUARANTINE,
    /** The content scan passed. */
    SCAN_PASS,
    /** The content scan rejected the candidate. */
    SCAN_REJECT,
    /** A retryable failure occurred. */
    FAILURE_RETRYABLE,
    /** A hard (non-retryable) failure occurred. */
    FAILURE_HARD,
    /** Retry after a retryable failure. */
    RETRY,
    /** Resume from suspended state. */
    RESUME
}
