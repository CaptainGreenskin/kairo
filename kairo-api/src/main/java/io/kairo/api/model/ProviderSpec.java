/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.model;

import java.util.Map;
import java.util.Objects;

/**
 * Inputs a {@link ProviderRegistry} hands to a {@link ProviderFactory} when constructing a
 * provider.
 *
 * <p>{@code baseUrl} and {@code modelName} override the factory's defaults when non-null; pass null
 * to use the preset's built-in URL / let the model be chosen later via {@code ModelConfig}. {@code
 * extras} is an escape hatch for provider-specific knobs (Bedrock region, Azure deployment id,
 * etc.) — most factories will ignore it.
 */
public record ProviderSpec(
        String apiKey, String baseUrl, String modelName, Map<String, Object> extras) {

    public ProviderSpec {
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    /** Most common case: just an API key. */
    public static ProviderSpec of(String apiKey) {
        return new ProviderSpec(apiKey, null, null, Map.of());
    }

    public static ProviderSpec of(String apiKey, String baseUrl) {
        return new ProviderSpec(apiKey, baseUrl, null, Map.of());
    }

    public ProviderSpec withBaseUrl(String url) {
        return new ProviderSpec(apiKey, url, modelName, extras);
    }

    public ProviderSpec withModel(String name) {
        return new ProviderSpec(apiKey, baseUrl, name, extras);
    }

    public ProviderSpec withExtras(Map<String, Object> extra) {
        Objects.requireNonNull(extra, "extras");
        return new ProviderSpec(apiKey, baseUrl, modelName, extra);
    }
}
