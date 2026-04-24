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
package io.kairo.api.model;

import io.kairo.api.Stable;

/**
 * Indicates that a model is temporarily unavailable for serving requests.
 *
 * <p>Typical causes include circuit-breaker open, provider maintenance windows, or capacity
 * protection in upstream gateways.
 */
@Stable(value = "Model unavailable exception; shape frozen since v0.4", since = "1.0.0")
public class ModelUnavailableException extends RuntimeException {

    private final String modelId;
    private final String reason;

    /**
     * Create a model unavailable exception.
     *
     * @param modelId model identifier
     * @param reason machine-readable reason, e.g. {@code circuit_open}
     * @param message human-readable message
     */
    public ModelUnavailableException(String modelId, String reason, String message) {
        super(message);
        this.modelId = modelId;
        this.reason = reason;
    }

    /**
     * @return model identifier.
     */
    public String getModelId() {
        return modelId;
    }

    /**
     * @return machine-readable unavailable reason.
     */
    public String getReason() {
        return reason;
    }
}
