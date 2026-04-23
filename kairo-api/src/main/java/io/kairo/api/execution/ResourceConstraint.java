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
package io.kairo.api.execution;

import io.kairo.api.Experimental;
import reactor.core.publisher.Mono;

/**
 * SPI for pluggable resource constraints that govern agent loop termination.
 *
 * <p>Implementations validate execution state against a constraint (e.g., iteration limit, token
 * budget, timeout) and determine the action to take when a constraint is violated.
 *
 * <p>Multiple constraints can be composed — the most severe action wins. See {@link ResourceAction}
 * for the severity ordering.
 *
 * @see ResourceValidation
 * @see ResourceAction
 * @see ResourceContext
 */
@Experimental("ResourceConstraint SPI — contract may change in v0.9")
public interface ResourceConstraint {

    /**
     * Validate execution state against this constraint.
     *
     * @param context current execution state
     * @return validation result indicating whether constraint is violated
     */
    Mono<ResourceValidation> validate(ResourceContext context);

    /**
     * Determine the action to take when this constraint is violated.
     *
     * @param validation the validation result
     * @return the action to take
     */
    ResourceAction onViolation(ResourceValidation validation);
}
