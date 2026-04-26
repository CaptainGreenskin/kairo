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
 * Ordered chain evaluator for {@link GuardrailPolicy} instances.
 *
 * <p>A chain evaluates policies sequentially, short-circuiting on {@link
 * GuardrailDecision.Action#DENY} and propagating {@link GuardrailDecision.Action#MODIFY} payloads
 * to subsequent policies.
 *
 * @since v0.7 (Experimental)
 */
@Experimental("Guardrail SPI — contract may change in v0.8")
public interface GuardrailChain {

    /**
     * Evaluate all registered policies against the given context.
     *
     * @param context the guardrail context to evaluate
     * @return a Mono emitting the final guardrail decision
     */
    Mono<GuardrailDecision> evaluate(GuardrailContext context);
}
