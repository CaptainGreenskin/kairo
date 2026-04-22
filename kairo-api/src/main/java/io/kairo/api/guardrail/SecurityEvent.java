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
import java.time.Instant;
import java.util.Map;

/**
 * Structured security event emitted after each guardrail policy evaluation.
 *
 * <p>Provides a single audit point for all security-relevant decisions (ALLOW, DENY, MODIFY, WARN)
 * across both user-defined policies and built-in MCP policies.
 *
 * @param timestamp when the event was created
 * @param type the security event classification
 * @param agentName the agent that owns the pipeline
 * @param targetName the tool or model name being guarded
 * @param phase the pipeline boundary point
 * @param policyName the policy that produced the decision
 * @param reason human-readable reason (may be empty for ALLOW)
 * @param attributes key-value pairs from the guardrail context metadata for observability
 * @since v0.7 (Experimental)
 */
@Experimental("Security Observability — contract may change in v0.8")
public record SecurityEvent(
        Instant timestamp,
        SecurityEventType type,
        String agentName,
        String targetName,
        GuardrailPhase phase,
        String policyName,
        String reason,
        Map<String, Object> attributes) {

    /**
     * Creates a {@link SecurityEvent} from a {@link GuardrailDecision} and its evaluation context.
     *
     * <p>The context's {@link GuardrailContext#metadata()} is passed through as event attributes,
     * preserving the original types.
     *
     * @param decision the policy evaluation outcome
     * @param context the guardrail evaluation context
     * @return a new security event with the appropriate type mapping
     */
    public static SecurityEvent fromDecision(GuardrailDecision decision, GuardrailContext context) {
        SecurityEventType type =
                switch (decision.action()) {
                    case ALLOW -> SecurityEventType.GUARDRAIL_ALLOW;
                    case DENY -> SecurityEventType.GUARDRAIL_DENY;
                    case MODIFY -> SecurityEventType.GUARDRAIL_MODIFY;
                    case WARN -> SecurityEventType.GUARDRAIL_WARN;
                };
        return new SecurityEvent(
                Instant.now(),
                type,
                context.agentName(),
                context.targetName(),
                context.phase(),
                decision.policyName(),
                decision.reason(),
                context.metadata() != null ? Map.copyOf(context.metadata()) : Map.of());
    }
}
