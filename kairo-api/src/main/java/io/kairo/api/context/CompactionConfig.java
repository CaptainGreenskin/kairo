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
package io.kairo.api.context;

import io.kairo.api.Stable;
import io.kairo.api.model.ModelProvider;

/**
 * Configuration for a compaction operation.
 *
 * @param targetTokens the target token count after compaction
 * @param preserveVerbatim whether to preserve verbatim-marked messages
 * @param modelProvider the model provider used for summarization-based compaction
 * @param partialDirection the direction for partial compaction
 * @param boundaryMarkerId the boundary marker ID for UP_TO partial compaction (nullable)
 */
@Stable(value = "Compaction config record; shape frozen since v0.1", since = "1.0.0")
public record CompactionConfig(
        int targetTokens,
        boolean preserveVerbatim,
        ModelProvider modelProvider,
        PartialDirection partialDirection,
        String boundaryMarkerId) {

    /** Direction for partial compaction. */
    public enum PartialDirection {
        /** Default: keep head (system+verbatim) + tail (last 5), compress middle. */
        FROM,
        /** Compress messages from start up to a boundary marker. Keep everything after. */
        UP_TO
    }

    /**
     * Backward-compatible constructor without partial direction fields.
     *
     * @param targetTokens the target token count after compaction
     * @param preserveVerbatim whether to preserve verbatim-marked messages
     * @param modelProvider the model provider used for summarization-based compaction
     */
    public CompactionConfig(
            int targetTokens, boolean preserveVerbatim, ModelProvider modelProvider) {
        this(targetTokens, preserveVerbatim, modelProvider, PartialDirection.FROM, null);
    }
}
