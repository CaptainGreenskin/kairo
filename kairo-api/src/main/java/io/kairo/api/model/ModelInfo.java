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

import io.kairo.api.Experimental;
import java.util.Objects;

/**
 * Resolution result from {@link ModelCatalog#resolve(String)}.
 *
 * <p>Bridges a model identifier to the provider that serves it and its runtime capabilities. The
 * {@link #providerName()} aligns with {@link ProviderRegistry} names so callers can directly call
 * {@code providerRegistry.create(info.providerName(), spec)}.
 *
 * @param providerName the provider name (e.g., "anthropic", "openai", "glm", "gemini")
 * @param canonicalModelId the canonical model identifier (e.g., "claude-sonnet-4-20250514")
 * @param capability the model's runtime capabilities
 */
@Experimental("ModelCatalog SPI v0.10")
public record ModelInfo(String providerName, String canonicalModelId, ModelCapability capability) {

    public ModelInfo {
        Objects.requireNonNull(providerName, "providerName must not be null");
        Objects.requireNonNull(canonicalModelId, "canonicalModelId must not be null");
        Objects.requireNonNull(capability, "capability must not be null");
    }
}
