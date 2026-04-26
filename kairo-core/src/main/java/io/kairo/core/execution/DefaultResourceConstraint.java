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
package io.kairo.core.execution;

import io.kairo.api.execution.*;
import java.time.Duration;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link ResourceConstraint} that consolidates the built-in iteration
 * limit, token budget, and timeout checks.
 *
 * <p>All violations from this constraint result in {@link ResourceAction#GRACEFUL_EXIT}.
 *
 * <p>Checks are evaluated in order: iteration limit → token budget → timeout. The first violation
 * found is reported (only one violation per validation).
 */
public class DefaultResourceConstraint implements ResourceConstraint {

    private final int maxIterations;
    private final long tokenBudget;
    private final Duration timeout;

    /**
     * Create a new default resource constraint.
     *
     * @param maxIterations maximum number of iterations allowed
     * @param tokenBudget maximum token consumption allowed
     * @param timeout maximum wall-clock duration allowed
     */
    public DefaultResourceConstraint(int maxIterations, long tokenBudget, Duration timeout) {
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be non-negative");
        }
        if (tokenBudget < 0) {
            throw new IllegalArgumentException("tokenBudget must be non-negative");
        }
        this.maxIterations = maxIterations;
        this.tokenBudget = tokenBudget;
        this.timeout = timeout != null ? timeout : Duration.ofHours(1);
    }

    @Override
    public Mono<ResourceValidation> validate(ResourceContext context) {
        // Check iteration limit
        if (context.iteration() >= maxIterations) {
            return Mono.just(
                    ResourceValidation.violated(
                            "Reached max iterations (" + maxIterations + ")",
                            Map.of(
                                    "maxIterations",
                                    maxIterations,
                                    "currentIteration",
                                    context.iteration())));
        }

        // Check token budget
        if (context.tokensUsed() >= tokenBudget) {
            return Mono.just(
                    ResourceValidation.violated(
                            "Exceeded token budget ("
                                    + context.tokensUsed()
                                    + "/"
                                    + tokenBudget
                                    + ")",
                            Map.of(
                                    "tokenBudget",
                                    tokenBudget,
                                    "tokensUsed",
                                    context.tokensUsed())));
        }

        // Check timeout
        if (context.elapsed().compareTo(timeout) >= 0) {
            return Mono.just(
                    ResourceValidation.violated(
                            "Exceeded timeout (" + timeout + ")",
                            Map.of(
                                    "timeout", timeout.toMillis(),
                                    "elapsed", context.elapsed().toMillis())));
        }

        return Mono.just(ResourceValidation.ok());
    }

    @Override
    public ResourceAction onViolation(ResourceValidation validation) {
        // All default constraints trigger graceful exit
        return ResourceAction.GRACEFUL_EXIT;
    }
}
