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
package io.kairo.core.model.anthropic;

import java.util.ArrayList;
import java.util.List;

/** Result of a cache break detection check. */
public class CacheCheckResult {
    private final int cacheReadTokens;
    private final int cacheCreationTokens;
    private final int inputTokens;
    private final List<String> reasons = new ArrayList<>();
    private boolean cacheBroken;

    public CacheCheckResult(int cacheReadTokens, int cacheCreationTokens, int inputTokens) {
        this.cacheReadTokens = cacheReadTokens;
        this.cacheCreationTokens = cacheCreationTokens;
        this.inputTokens = inputTokens;
    }

    public double hitRatio() {
        int total = cacheReadTokens + cacheCreationTokens;
        return total == 0 ? 0.0 : (double) cacheReadTokens / total;
    }

    public int cacheReadTokens() {
        return cacheReadTokens;
    }

    public int cacheCreationTokens() {
        return cacheCreationTokens;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public boolean isCacheBroken() {
        return cacheBroken;
    }

    public List<String> reasons() {
        return reasons;
    }

    public void addReason(String reason) {
        reasons.add(reason);
    }

    public void setCacheBroken(boolean broken) {
        this.cacheBroken = broken;
    }
}
