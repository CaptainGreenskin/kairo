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
package io.kairo.core.memory;

import io.kairo.api.memory.EmbeddingProvider;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A no-op embedding provider that produces no embeddings.
 *
 * <p>Used as a default when no real embedding service is configured. All operations return empty
 * reactive types, signaling that vector-based similarity search is unavailable.
 */
public class NoopEmbeddingProvider implements EmbeddingProvider {

    @Override
    public Mono<float[]> embed(String text) {
        return Mono.empty();
    }

    @Override
    public Flux<float[]> embedAll(List<String> texts) {
        return Flux.empty();
    }

    @Override
    public int dimensions() {
        return 0;
    }
}
