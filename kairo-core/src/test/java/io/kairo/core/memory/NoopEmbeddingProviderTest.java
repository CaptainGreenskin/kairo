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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class NoopEmbeddingProviderTest {

    private final NoopEmbeddingProvider provider = new NoopEmbeddingProvider();

    @Test
    @DisplayName("embed returns empty Mono")
    void testEmbedReturnsEmpty() {
        StepVerifier.create(provider.embed("some text")).verifyComplete();
    }

    @Test
    @DisplayName("embedAll returns empty Flux")
    void testEmbedAllReturnsEmpty() {
        StepVerifier.create(provider.embedAll(List.of("text1", "text2", "text3"))).verifyComplete();
    }

    @Test
    @DisplayName("dimensions returns 0")
    void testDimensionsReturnsZero() {
        assertEquals(0, provider.dimensions());
    }
}
