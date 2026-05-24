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
 * The black-box that decides whether a skill triggers on a given prompt. Plug in your preferred LLM
 * (Anthropic, GLM, local) by adapting it to this interface. Production wiring usually calls a
 * {@code claude -p} subprocess or an MCP-style judge model.
 *
 * <p>Implementations should be deterministic for the same {@code (skillName, description, prompt)}
 * tuple to make the eval reproducible — set temperature=0 on the underlying model.
 */
@FunctionalInterface
public interface TriggerDecider {

    /**
     * @return true iff the skill, given {@code description}, would trigger on {@code prompt}
     */
    Mono<Boolean> wouldTrigger(String skillName, String description, String prompt);
}
