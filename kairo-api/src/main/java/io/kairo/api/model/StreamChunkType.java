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

import io.kairo.api.Stable;

/** Type of a streaming response chunk. */
@Stable(value = "Streaming chunk type enum; values frozen since v0.3", since = "1.0.0")
public enum StreamChunkType {

    /** A text content chunk. */
    TEXT,

    /** A thinking / chain-of-thought content chunk. */
    THINKING,

    /** Start of a tool use block. */
    TOOL_USE_START,

    /** Incremental tool argument delta. */
    TOOL_USE_DELTA,

    /** End of a tool use block. */
    TOOL_USE_END,

    /** Stream is complete. */
    DONE,

    /** An error occurred during streaming. */
    ERROR,

    /**
     * Final usage frame carrying authoritative token counts.
     *
     * <p>Emitted at most once per stream, immediately before {@link #DONE}, by providers that
     * surface usage out-of-band (OpenAI/GLM trailing chunk with empty {@code choices} when {@code
     * stream_options.include_usage=true}). Counts live in {@link StreamChunk#metadata()} under keys
     * {@code gen_ai.usage.input_tokens} / {@code gen_ai.usage.output_tokens} (both {@link
     * Integer}). Use {@link StreamChunk#usage(int, int)} to build one. Consumers that don't need
     * usage can ignore this type — it is purely additive.
     */
    USAGE
}
