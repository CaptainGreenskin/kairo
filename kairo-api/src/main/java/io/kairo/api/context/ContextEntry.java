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

/**
 * A single piece of context collected from a {@link ContextSource}.
 *
 * <p>This is a carrier object that associates collected content with its source metadata, used
 * during the assembly phase in {@link ContextBuilder}.
 *
 * @param sourceName the name of the source that provided this entry
 * @param priority the priority of the source
 * @param content the collected context content
 */
public record ContextEntry(String sourceName, int priority, String content) {

    /**
     * Create a new ContextEntry.
     *
     * @param sourceName the name of the source (must not be null)
     * @param priority the priority value
     * @param content the context content (must not be null)
     */
    public ContextEntry {
        java.util.Objects.requireNonNull(sourceName, "sourceName must not be null");
        java.util.Objects.requireNonNull(content, "content must not be null");
    }
}
