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

import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolDefinition;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Detects KV-cache breaks by comparing consecutive API call usage metrics. Tracks system prompt
 * hash, tool schema hash, and call timestamps to diagnose why cache_read_tokens dropped.
 */
public class CacheBreakDetector {
    private long lastSystemPromptHash;
    private long lastToolSchemaHash;
    private int lastCacheReadTokens;
    private Instant lastCallTime;
    private boolean hasPreviousCall;

    /** Allow injecting a custom clock for testing TTL expiry. */
    private Instant nowOverride;

    public void setNowOverride(Instant now) {
        this.nowOverride = now;
    }

    private Instant now() {
        return nowOverride != null ? nowOverride : Instant.now();
    }

    public CacheCheckResult check(
            ModelResponse.Usage usage, String systemPrompt, List<ToolDefinition> tools) {
        long currentSysHash = systemPrompt != null ? systemPrompt.hashCode() : 0;
        long currentToolHash = tools != null ? tools.hashCode() : 0;
        int currentCacheRead = usage.cacheReadTokens();

        CacheCheckResult result =
                new CacheCheckResult(
                        currentCacheRead, usage.cacheCreationTokens(), usage.inputTokens());

        if (hasPreviousCall
                && lastCacheReadTokens > 0
                && currentCacheRead < lastCacheReadTokens * 0.95) {
            if (currentSysHash != lastSystemPromptHash) {
                result.addReason("system_prompt_changed");
            }
            if (currentToolHash != lastToolSchemaHash) {
                result.addReason("tool_schema_changed");
            }
            if (lastCallTime != null && Duration.between(lastCallTime, now()).toMinutes() > 5) {
                result.addReason("ttl_expired");
            }
            result.setCacheBroken(true);
        }

        // Update state
        lastSystemPromptHash = currentSysHash;
        lastToolSchemaHash = currentToolHash;
        lastCacheReadTokens = currentCacheRead;
        lastCallTime = now();
        hasPreviousCall = true;

        return result;
    }
}
