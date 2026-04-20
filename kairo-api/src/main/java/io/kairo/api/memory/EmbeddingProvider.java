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
package io.kairo.api.memory;

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SPI for generating vector embeddings from text.
 *
 * <p>Implementations may delegate to external embedding services (e.g., OpenAI, Cohere, local
 * models). Used by memory stores that support vector similarity search.
 */
public interface EmbeddingProvider {

    /**
     * Generate an embedding vector for the given text.
     *
     * @param text the text to embed
     * @return a Mono emitting the embedding vector
     */
    Mono<float[]> embed(String text);

    /**
     * Generate embedding vectors for multiple texts in batch.
     *
     * <p>The default implementation calls {@link #embed(String)} for each text individually.
     * Implementations SHOULD override this for batch efficiency.
     *
     * @param texts the texts to embed
     * @return a Flux emitting embedding vectors in the same order as the input texts
     */
    default Flux<float[]> embedAll(List<String> texts) {
        return Flux.fromIterable(texts).concatMap(this::embed);
    }

    /**
     * Return the dimensionality of the embedding vectors produced by this provider.
     *
     * @return the number of dimensions in each embedding vector
     */
    int dimensions();
}
