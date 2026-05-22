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
package io.kairo.evolution.eval;

import reactor.core.publisher.Mono;

/**
 * The brain of the description optimisation loop. Given the current description and the failed /
 * false-trigger lists from this iteration, return a new description proposal.
 *
 * <p>Hosts adapt their preferred LLM (Anthropic, GLM, local) to this interface. Mirrors the {@code
 * improve_description()} function in deer-flow's run_loop.py.
 */
@FunctionalInterface
public interface LlmDescriptionOptimizer {

    /**
     * @return a proposed new description (may be unchanged if the LLM decided not to revise).
     */
    Mono<String> improve(DescriptionOptimizationContext ctx);
}
