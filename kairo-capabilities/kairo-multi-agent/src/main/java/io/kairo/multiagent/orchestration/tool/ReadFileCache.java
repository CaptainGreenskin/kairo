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
package io.kairo.multiagent.orchestration.tool;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Team-scoped cache for read_file results.
 *
 * <p>Full cache is cleared on any mutation tool execution (bash, write_file, edit_file).
 * Thread-safe for concurrent access across DAG-layer agents.
 *
 * <p><strong>Known limitation:</strong> A narrow race window exists where an agent may cache a file
 * that another agent is about to mutate. This is accepted; the next mutation will clear the stale
 * entry and subsequent reads will re-fetch from disk.
 *
 * @since v0.10
 */
public class ReadFileCache {

    private static final Logger log = LoggerFactory.getLogger(ReadFileCache.class);

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private final AtomicInteger hits = new AtomicInteger(0);
    private final AtomicInteger misses = new AtomicInteger(0);
    private final AtomicInteger invalidations = new AtomicInteger(0);

    /** Get cached content for path. Returns {@link Optional#empty()} on miss. */
    public Optional<String> get(String path) {
        String content = cache.get(path);
        if (content != null) {
            hits.incrementAndGet();
            return Optional.of(content);
        }
        misses.incrementAndGet();
        return Optional.empty();
    }

    /** Store read_file result. Null path or content is silently ignored. */
    public void put(String path, String content) {
        if (path != null && content != null) {
            cache.put(path, content);
        }
    }

    /**
     * Clear entire cache. Called on any mutation tool execution.
     *
     * @param triggerToolName the tool that triggered invalidation, for logging
     */
    public void invalidateAll(String triggerToolName) {
        int size = cache.size();
        cache.clear();
        int count = invalidations.incrementAndGet();
        if (size > 0) {
            log.info(
                    "ReadFileCache invalidated ({} entries cleared) by mutation tool '{}'"
                            + " (invalidation #{})",
                    size,
                    triggerToolName,
                    count);
        }
    }

    /** Log summary stats at end of team execution. */
    public void logSummary() {
        int h = hits.get(), m = misses.get();
        int total = h + m;
        if (total > 0) {
            double ratio = (double) h / total * 100;
            log.info(
                    "ReadFileCache summary: {} hits, {} misses ({} % hit rate), {} invalidations",
                    h, m, String.format("%.1f", ratio), invalidations.get());
        }
    }

    /** Number of cache hits. */
    public int getHits() {
        return hits.get();
    }

    /** Number of cache misses. */
    public int getMisses() {
        return misses.get();
    }

    /** Number of full-cache invalidations performed. */
    public int getInvalidations() {
        return invalidations.get();
    }

    /** Current number of cached entries. */
    public int size() {
        return cache.size();
    }
}
