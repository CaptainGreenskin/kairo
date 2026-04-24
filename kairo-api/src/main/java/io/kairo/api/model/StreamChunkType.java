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
    ERROR
}
