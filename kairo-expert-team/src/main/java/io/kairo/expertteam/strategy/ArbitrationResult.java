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

import java.util.Objects;

/**
 * Result of an Architect arbitration decision.
 *
 * @param decision the type of arbitration decision made
 * @param resolvedOutput the final artifact (accepted output, merged resolution, or revised
 *     instruction depending on the decision)
 * @param rationale the Architect's reasoning for the decision
 * @since v0.10 (Experimental)
 */
public record ArbitrationResult(
        ArbitrationDecision decision, String resolvedOutput, String rationale) {

    public ArbitrationResult {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(resolvedOutput, "resolvedOutput must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
    }
}
