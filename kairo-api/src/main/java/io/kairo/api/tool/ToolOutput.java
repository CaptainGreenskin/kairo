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
package io.kairo.api.tool;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Sealed hierarchy representing the possible output shapes of a tool execution.
 *
 * @since 1.2.0
 */
public sealed interface ToolOutput {

    /** Plain-text output (most common case). */
    record Text(String content) implements ToolOutput {}

    /** Structured key-value output for programmatic consumption. */
    record Structured(Map<String, Object> data) implements ToolOutput {}

    /** Binary blob output with MIME type (images, archives, etc.). */
    record Binary(byte[] data, String mime) implements ToolOutput {
        @Override
        public boolean equals(Object o) {
            return o instanceof Binary b && mime.equals(b.mime) && Arrays.equals(data, b.data);
        }

        @Override
        public int hashCode() {
            return 31 * mime.hashCode() + Arrays.hashCode(data);
        }
    }

    /** Truncated output when the full result exceeds the output budget. */
    record Truncated(String visible, long totalBytes, Optional<URI> fullOutput)
            implements ToolOutput {}
}
