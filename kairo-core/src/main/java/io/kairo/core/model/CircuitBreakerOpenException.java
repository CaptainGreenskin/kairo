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
package io.kairo.core.model;

/** Thrown when a model call is rejected because the circuit breaker is in OPEN state. */
public class CircuitBreakerOpenException extends RuntimeException {

    private final String modelId;

    /**
     * Create a new circuit breaker open exception.
     *
     * @param modelId the model whose circuit breaker is open
     */
    public CircuitBreakerOpenException(String modelId) {
        super("Circuit breaker is open for model: " + modelId);
        this.modelId = modelId;
    }

    /**
     * Get the model identifier.
     *
     * @return the model ID
     */
    public String getModelId() {
        return modelId;
    }
}
