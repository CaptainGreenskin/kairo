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
import reactor.core.publisher.Mono;

/**
 * SPI for reviewing agent execution and producing evolution outcomes.
 *
 * <p>Implementations analyze conversation history, tool usage, and failure patterns to propose
 * skill creation, skill patches, or memory updates.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change in v0.10")
public interface EvolutionPolicy {

    Mono<EvolutionOutcome> review(EvolutionContext context);

    default int order() {
        return 0;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
