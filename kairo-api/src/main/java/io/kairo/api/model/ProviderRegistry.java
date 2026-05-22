/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.model;

import io.kairo.api.Experimental;
import java.util.Set;
import java.util.function.Function;

/**
 * Lookup-by-name for {@link ModelProvider} factories. Lets host code (CLI, Spring auto-config,
 * application configuration) say {@code registry.create("minimax", apiKey, baseUrl)} instead of
 * hard-coding switch statements per provider name.
 *
 * <p>A provider is registered as a {@link ProviderFactory} — given a {@link ProviderSpec} (api key,
 * optional base URL, optional model name, optional extra config), it returns a configured {@code
 * ModelProvider}. Implementations are typically thin wrappers over the static factory methods in
 * {@code io.kairo.core.model.ProviderPresets}.
 *
 * <p>Names are case-insensitive. Last registration wins on collision.
 *
 * @since 1.3 (Experimental)
 */
@Experimental("Provider SPI — contract may change in v1.x")
public interface ProviderRegistry {

    /** Register a provider factory under {@code name}. Case-insensitive. */
    void register(String name, ProviderFactory factory);

    /** True when a provider is registered under {@code name}. */
    boolean isRegistered(String name);

    /** All registered names in registration order. */
    Set<String> names();

    /**
     * Construct a {@link ModelProvider} for {@code name} using {@code spec}. Throws {@link
     * IllegalArgumentException} when no factory is registered, when the factory rejects the spec
     * (missing api key, etc.), or when the spec is otherwise invalid.
     */
    ModelProvider create(String name, ProviderSpec spec);

    /** Convenience: register a factory that only needs an API key. */
    default void registerApiKeyOnly(String name, Function<String, ModelProvider> factory) {
        register(
                name,
                spec -> {
                    if (spec.apiKey() == null || spec.apiKey().isBlank()) {
                        throw new IllegalArgumentException(
                                "Provider '" + name + "' requires an API key");
                    }
                    return factory.apply(spec.apiKey());
                });
    }

    /** Convenience: shorthand for {@code create(name, ProviderSpec.of(apiKey))}. */
    default ModelProvider create(String name, String apiKey) {
        return create(name, ProviderSpec.of(apiKey));
    }
}
