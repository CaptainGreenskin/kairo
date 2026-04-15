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
package io.kairo.core.prompt;

import io.kairo.api.context.SystemPromptSegment;
import java.util.List;

/**
 * Result of building a system prompt with static/dynamic boundary separation.
 *
 * <p>The static prefix contains content that rarely changes between requests and can be cached by
 * the Anthropic API via {@code cache_control} markers. The dynamic suffix contains content that
 * changes per request (e.g., feature gates, runtime context).
 *
 * @param staticPrefix the cacheable portion of the system prompt (before the boundary)
 * @param dynamicSuffix the per-request portion of the system prompt (after the boundary)
 * @param fullPrompt the complete assembled system prompt
 * @param segments the cache-scoped segments for multi-block model API usage
 */
public record SystemPromptResult(
        String staticPrefix,
        String dynamicSuffix,
        String fullPrompt,
        List<SystemPromptSegment> segments) {

    /** Backward-compat constructor. */
    public SystemPromptResult(String staticPrefix, String dynamicSuffix, String fullPrompt) {
        this(staticPrefix, dynamicSuffix, fullPrompt, List.of());
    }

    /** Whether this result has segments. */
    public boolean hasSegments() {
        return segments != null && !segments.isEmpty();
    }

    /** Whether this result has a meaningful static/dynamic split. */
    public boolean hasBoundary() {
        return staticPrefix != null
                && !staticPrefix.isEmpty()
                && dynamicSuffix != null
                && !dynamicSuffix.isEmpty();
    }
}
