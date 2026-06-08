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
import java.util.Optional;
import java.util.Set;

/**
 * SPI for resolving model names (or aliases) to their provider identity and capabilities.
 *
 * <p>Bridges the gap between {@link ProviderRegistry} (which maps provider names to factories) and
 * model identifiers that callers work with. Given {@code "claude-sonnet-4"}, a catalog can resolve
 * it to {@code ModelInfo("anthropic", "claude-sonnet-4", capability)}, so the caller can then call
 * {@code providerRegistry.create("anthropic", spec)} without URL-sniffing heuristics.
 *
 * <p>Implementations must be thread-safe.
 *
 * @see ModelInfo
 * @see NoopModelCatalog
 */
@Experimental("ModelCatalog SPI v0.10")
public interface ModelCatalog {

    /**
     * Resolve a model name or alias to its provider and capabilities.
     *
     * @param modelNameOrAlias the model identifier (e.g., "claude-sonnet-4-20250514") or alias
     *     (e.g., "sonnet")
     * @return the resolved model info, or empty if the model is not known
     */
    Optional<ModelInfo> resolve(String modelNameOrAlias);

    /**
     * Register a model entry.
     *
     * @param modelId the canonical model identifier
     * @param info the model info
     */
    void register(String modelId, ModelInfo info);

    /**
     * Register an alias that maps to a canonical model ID.
     *
     * @param alias the alias (e.g., "sonnet")
     * @param canonicalModelId the canonical model ID it resolves to (e.g., "claude-sonnet-4")
     */
    void registerAlias(String alias, String canonicalModelId);

    /**
     * All registered canonical model IDs (excludes aliases).
     *
     * @return an unmodifiable set of model IDs
     */
    Set<String> knownModels();
}
