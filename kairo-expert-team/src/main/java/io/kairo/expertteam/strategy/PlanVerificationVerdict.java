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

import java.util.List;
import java.util.Objects;

/**
 * Result of verifying a plan execution against its original intent.
 *
 * @param outcome the verification outcome
 * @param reason human-readable explanation
 * @param issues specific issues found during verification
 */
public record PlanVerificationVerdict(
        VerificationOutcome outcome, String reason, List<String> issues) {

    public PlanVerificationVerdict {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public enum VerificationOutcome {
        VERIFIED,
        PARTIAL,
        FAILED
    }

    public static PlanVerificationVerdict verified(String reason) {
        return new PlanVerificationVerdict(VerificationOutcome.VERIFIED, reason, List.of());
    }

    public static PlanVerificationVerdict partial(String reason, List<String> issues) {
        return new PlanVerificationVerdict(VerificationOutcome.PARTIAL, reason, issues);
    }

    public static PlanVerificationVerdict failed(String reason, List<String> issues) {
        return new PlanVerificationVerdict(VerificationOutcome.FAILED, reason, issues);
    }

    public boolean isSuccess() {
        return outcome == VerificationOutcome.VERIFIED;
    }
}
