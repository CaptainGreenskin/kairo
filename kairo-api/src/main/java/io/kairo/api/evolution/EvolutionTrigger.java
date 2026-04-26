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
package io.kairo.api.evolution;

import io.kairo.api.Experimental;

/**
 * SPI for determining when the evolution subsystem should trigger reviews.
 *
 * <p>Implementations inspect the current {@link EvolutionContext} and decide whether skill or
 * memory reviews are warranted.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("Self-Evolution Trigger SPI — contract may change in v0.10")
public interface EvolutionTrigger {

    boolean shouldReviewSkill(EvolutionContext context);

    boolean shouldReviewMemory(EvolutionContext context);

    default String reason() {
        return "default-trigger";
    }
}
