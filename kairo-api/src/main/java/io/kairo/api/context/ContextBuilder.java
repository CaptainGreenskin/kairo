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
import java.util.List;

/**
 * Assembles context from multiple {@link ContextSource} instances into a structured list of {@link
 * ContextEntry} objects.
 *
 * <p>The builder is responsible for:
 *
 * <ul>
 *   <li>Managing the list of registered sources
 *   <li>Filtering inactive sources
 *   <li>Collecting content from all active sources
 *   <li>Ordering results by priority
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ContextBuilder builder = new DefaultContextBuilder()
 *     .addSource(new DateContextSource())
 *     .addSource(new SystemInfoContextSource());
 *
 * List<ContextEntry> entries = builder.build();
 * }</pre>
 */
@Stable(value = "Context builder SPI; shape frozen since v0.1", since = "1.0.0")
public interface ContextBuilder {

    /**
     * Register a context source.
     *
     * @param source the source to add (must not be null)
     * @return this builder for chaining
     */
    ContextBuilder addSource(ContextSource source);

    /**
     * Remove a context source by name.
     *
     * @param name the source name to remove
     * @return this builder for chaining
     */
    ContextBuilder removeSource(String name);

    /**
     * Assemble context from all active, registered sources.
     *
     * <p>Sources are collected, filtered by {@link ContextSource#isActive()}, and ordered by {@link
     * ContextSource#priority()}. The returned entries are ready for injection into the LLM prompt.
     *
     * @return the assembled context entries (never null, may be empty)
     */
    List<ContextEntry> build();

    /**
     * Clear any cached context data.
     *
     * <p>Sources with session-level caching should invalidate their cache when this method is
     * called (e.g., on session switch).
     */
    void invalidateCache();
}
