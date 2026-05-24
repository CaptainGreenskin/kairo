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
package io.kairo.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding provider configuration ({@code kairo.embedding.*}).
 *
 * <p>Configures the embedding provider used for semantic memory retrieval. When set to {@code
 * "noop"}, embedding-based retrieval is disabled and only keyword search is available.
 */
@ConfigurationProperties(prefix = "kairo.embedding")
public class EmbeddingProperties {

    /**
     * Embedding provider type. Determines which embedding backend is used for vectorizing memory
     * entries.
     *
     * <p>Valid values: {@code "noop"} (disabled — no embedding), or a custom provider identifier
     * registered via SPI.
     *
     * <p>Default: {@code "noop"}
     */
    private String provider = "noop";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
