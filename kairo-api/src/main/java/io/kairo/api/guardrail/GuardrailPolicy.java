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
import reactor.core.publisher.Mono;

/**
 * SPI for guardrail policies that evaluate requests/responses at pipeline boundaries.
 *
 * <p>Implementations inspect a {@link GuardrailContext} and return a {@link GuardrailDecision}
 * indicating whether to allow, deny, modify, or warn.
 *
 * <p>Policies are evaluated in {@link #order()} order within a {@link GuardrailChain}. Lower order
 * values execute first. A {@link GuardrailDecision.Action#DENY} decision short-circuits the chain.
 *
 * @since v0.7 (Experimental)
 */
@Experimental("Guardrail SPI — contract may change in v0.8")
public interface GuardrailPolicy {

    /**
     * Evaluate the given guardrail context and return a decision.
     *
     * @param context the context carrying phase, identity, payload, and metadata
     * @return a Mono emitting the guardrail decision
     */
    Mono<GuardrailDecision> evaluate(GuardrailContext context);

    /**
     * The evaluation order of this policy. Lower values are evaluated first. Default is {@code 0}.
     *
     * @return the order value
     */
    default int order() {
        return 0;
    }

    /**
     * The human-readable name of this policy, used in logging and decisions. Defaults to the simple
     * class name.
     *
     * @return the policy name
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
