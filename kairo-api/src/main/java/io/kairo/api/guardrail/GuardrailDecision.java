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
package io.kairo.api.guardrail;

import io.kairo.api.Experimental;

/**
 * The outcome of a single {@link GuardrailPolicy} evaluation.
 *
 * <p>Use the static factory methods ({@link #allow}, {@link #deny}, {@link #modify}, {@link #warn})
 * to construct instances.
 *
 * @param action the action to take
 * @param reason human-readable reason — never null or blank
 * @param policyName the name of the policy that produced this decision
 * @param modifiedPayload the modified payload (only meaningful for {@link Action#MODIFY})
 * @since v0.7 (Experimental)
 */
@Experimental("Guardrail SPI — contract may change in v0.8")
public record GuardrailDecision(
        Action action, String reason, String policyName, GuardrailPayload modifiedPayload) {

    /** The action a guardrail policy may take. */
    public enum Action {
        /** Allow the request/response to proceed unchanged. */
        ALLOW,
        /** Deny and halt the pipeline — no further policies are evaluated. */
        DENY,
        /** Replace the payload and continue evaluation with the modified version. */
        MODIFY,
        /** Log a warning but allow the pipeline to continue. */
        WARN
    }

    /** Create an ALLOW decision with a default reason of {@code "allowed"}. */
    public static GuardrailDecision allow(String policyName) {
        return new GuardrailDecision(Action.ALLOW, "allowed", policyName, null);
    }

    /**
     * Create an ALLOW decision with a custom reason.
     *
     * @param reason human-readable reason (must not be null or blank)
     * @param policyName the name of the policy that produced this decision
     * @return a new ALLOW decision
     * @throws IllegalArgumentException if reason is null or blank
     */
    public static GuardrailDecision allow(String reason, String policyName) {
        requireNonBlank(reason, "reason");
        return new GuardrailDecision(Action.ALLOW, reason, policyName, null);
    }

    /** Create a DENY decision with a reason. */
    public static GuardrailDecision deny(String reason, String policyName) {
        requireNonBlank(reason, "reason");
        return new GuardrailDecision(Action.DENY, reason, policyName, null);
    }

    /** Create a MODIFY decision with a replacement payload. */
    public static GuardrailDecision modify(
            GuardrailPayload modified, String reason, String policyName) {
        requireNonBlank(reason, "reason");
        return new GuardrailDecision(Action.MODIFY, reason, policyName, modified);
    }

    /** Create a WARN decision with a reason. */
    public static GuardrailDecision warn(String reason, String policyName) {
        requireNonBlank(reason, "reason");
        return new GuardrailDecision(Action.WARN, reason, policyName, null);
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
    }
}
