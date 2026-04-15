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
package io.kairo.core.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages fallback model chain for error recovery.
 *
 * <p>When the primary model fails with a server error, this manager provides the next fallback
 * model in the configured chain. The fallback index advances on each call to {@link
 * #nextFallback()} and can be reset on successful calls via {@link #reset()}.
 *
 * <p>Thread-safe: uses {@link AtomicInteger} for the fallback index.
 */
public class ModelFallbackManager {

    private final List<String> fallbackModels;
    private final AtomicInteger fallbackIndex = new AtomicInteger(0);

    /**
     * Create a new fallback manager with the given model chain.
     *
     * @param fallbackModels ordered list of fallback model IDs, or null for no fallbacks
     */
    public ModelFallbackManager(List<String> fallbackModels) {
        this.fallbackModels = fallbackModels != null ? List.copyOf(fallbackModels) : List.of();
    }

    /**
     * Check whether there are remaining fallback models to try.
     *
     * @return true if at least one more fallback is available
     */
    public boolean hasFallback() {
        return fallbackIndex.get() < fallbackModels.size();
    }

    /**
     * Get the next fallback model ID and advance the index.
     *
     * @return the next fallback model ID, or null if exhausted
     */
    public String nextFallback() {
        int idx = fallbackIndex.getAndIncrement();
        if (idx >= fallbackModels.size()) {
            return null;
        }
        return fallbackModels.get(idx);
    }

    /** Reset the fallback index to the beginning (call after a successful model response). */
    public void reset() {
        fallbackIndex.set(0);
    }

    /**
     * Get the current effective model, considering fallback state.
     *
     * @param primaryModel the primary model ID
     * @return the current model ID (primary if no fallback has been activated)
     */
    public String currentModel(String primaryModel) {
        int idx = fallbackIndex.get();
        if (idx == 0) {
            return primaryModel;
        }
        if (idx - 1 < fallbackModels.size()) {
            return fallbackModels.get(idx - 1);
        }
        return primaryModel;
    }
}
