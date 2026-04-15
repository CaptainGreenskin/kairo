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
 * Snapshot of the current context state, used to decide compaction triggers.
 *
 * @param totalTokens the total token capacity
 * @param usedTokens tokens currently consumed
 * @param pressure usage pressure ratio (0.0 to 1.0)
 * @param messageCount the number of messages in context
 * @param contextWindow the resolved context window size from the model registry (0 if unknown)
 */
public record ContextState(
        int totalTokens, int usedTokens, float pressure, int messageCount, int contextWindow) {

    /**
     * Backward-compatible constructor without contextWindow.
     *
     * @param totalTokens the total token capacity
     * @param usedTokens tokens currently consumed
     * @param pressure usage pressure ratio (0.0 to 1.0)
     * @param messageCount the number of messages in context
     */
    public ContextState(int totalTokens, int usedTokens, float pressure, int messageCount) {
        this(totalTokens, usedTokens, pressure, messageCount, 0);
    }
}
