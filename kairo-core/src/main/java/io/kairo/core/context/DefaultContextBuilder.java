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
package io.kairo.core.context;

import io.kairo.api.context.ContextBuilder;
import io.kairo.api.context.ContextBuilderConfig;
import io.kairo.api.context.ContextEntry;
import io.kairo.api.context.ContextSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ContextBuilder}.
 *
 * <p>Collects context from all registered, active sources, orders them by priority, and returns a
 * list of {@link ContextEntry} objects ready for prompt injection.
 *
 * <p>Thread-safe: sources can be added/removed concurrently with build calls.
 */
public class DefaultContextBuilder implements ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextBuilder.class);

    private final CopyOnWriteArrayList<ContextSource> sources;
    private final ContextBuilderConfig config;

    /** Creates a builder with default configuration. */
    public DefaultContextBuilder() {
        this(ContextBuilderConfig.defaults());
    }

    /**
     * Creates a builder with the specified configuration.
     *
     * @param config the assembly configuration (must not be null)
     */
    public DefaultContextBuilder(ContextBuilderConfig config) {
        this.config = config;
        this.sources = new CopyOnWriteArrayList<>();
    }

    @Override
    public ContextBuilder addSource(ContextSource source) {
        sources.add(source);
        log.debug(
                "Registered context source: {} (priority={})", source.getName(), source.priority());
        return this;
    }

    @Override
    public ContextBuilder removeSource(String name) {
        boolean removed = sources.removeIf(s -> s.getName().equals(name));
        if (removed) {
            log.debug("Removed context source: {}", name);
        }
        return this;
    }

    @Override
    public List<ContextEntry> build() {
        List<ContextEntry> entries = new ArrayList<>();

        for (ContextSource source : sources) {
            if (!source.isActive()) {
                log.debug("Skipping inactive source: {}", source.getName());
                continue;
            }

            try {
                String content = source.collect();
                if (content == null || content.isEmpty()) {
                    log.debug("Source {} returned empty content, skipping", source.getName());
                    continue;
                }

                // Truncate if configured
                if (config.maxEntryLength() > 0 && content.length() > config.maxEntryLength()) {
                    content = content.substring(0, config.maxEntryLength()) + "\n...[truncated]";
                }

                entries.add(new ContextEntry(source.getName(), source.priority(), content));
            } catch (Exception e) {
                log.warn("Failed to collect from source {}: {}", source.getName(), e.getMessage());
            }
        }

        // Sort by priority (lower = more important = first)
        entries.sort(Comparator.comparingInt(ContextEntry::priority));

        // Limit entry count if configured
        if (config.maxEntries() > 0 && entries.size() > config.maxEntries()) {
            entries = entries.subList(0, config.maxEntries());
        }

        log.debug("Assembled {} context entries from {} sources", entries.size(), sources.size());
        return entries;
    }

    @Override
    public void invalidateCache() {
        log.debug("Invalidating context cache");
        // Sources that implement caching should handle this internally.
        // We notify all sources by clearing any cached state we hold.
        // Future: add ContextSource.invalidateCache() if needed.
    }
}
