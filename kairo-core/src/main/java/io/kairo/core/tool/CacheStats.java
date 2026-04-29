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
package io.kairo.core.tool;

import java.util.concurrent.atomic.LongAdder;

/** Mutable, thread-safe cache statistics for a single tool. */
public final class CacheStats {
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final LongAdder bytesSaved = new LongAdder();

    public void recordHit(int contentLength) {
        hits.increment();
        bytesSaved.add(contentLength);
    }

    public void recordMiss() {
        misses.increment();
    }

    public void recordEviction() {
        evictions.increment();
    }

    public long hits() {
        return hits.sum();
    }

    public long misses() {
        return misses.sum();
    }

    public long evictions() {
        return evictions.sum();
    }

    public long bytesSaved() {
        return bytesSaved.sum();
    }

    /** Returns hit ratio in [0.0, 1.0], or 0.0 if no requests yet. */
    public double hitRatio() {
        long total = hits.sum() + misses.sum();
        return total == 0 ? 0.0 : (double) hits.sum() / total;
    }

    @Override
    public String toString() {
        return String.format(
                "CacheStats{hits=%d, misses=%d, ratio=%.1f%%, evictions=%d, bytesSaved=%d}",
                hits(), misses(), hitRatio() * 100, evictions(), bytesSaved());
    }
}
