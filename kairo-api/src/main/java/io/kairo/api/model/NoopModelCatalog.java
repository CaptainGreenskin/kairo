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

import java.util.Optional;
import java.util.Set;

/**
 * Empty {@link ModelCatalog} that resolves nothing.
 *
 * <p>Used as the default when no model catalog is configured.
 */
public final class NoopModelCatalog implements ModelCatalog {

    public static final NoopModelCatalog INSTANCE = new NoopModelCatalog();

    private NoopModelCatalog() {}

    @Override
    public Optional<ModelInfo> resolve(String modelNameOrAlias) {
        return Optional.empty();
    }

    @Override
    public void register(String modelId, ModelInfo info) {}

    @Override
    public void registerAlias(String alias, String canonicalModelId) {}

    @Override
    public Set<String> knownModels() {
        return Set.of();
    }
}
