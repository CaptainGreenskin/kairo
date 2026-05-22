/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.model;

/** Builds a {@link ModelProvider} from a {@link ProviderSpec}. Functional interface. */
@FunctionalInterface
public interface ProviderFactory {

    ModelProvider create(ProviderSpec spec);
}
