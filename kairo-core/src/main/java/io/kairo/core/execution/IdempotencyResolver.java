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
package io.kairo.core.execution;

import io.kairo.api.tool.Idempotent;
import io.kairo.api.tool.ToolHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Resolves the replay strategy for tools during crash recovery based on their idempotency
 * annotations.
 *
 * <p>Tools annotated with {@link Idempotent @Idempotent} are safe to re-execute on recovery. All
 * other tools (annotated with {@link io.kairo.api.tool.NonIdempotent @NonIdempotent} or
 * unannotated) default to returning cached results from the event log — the safe default per
 * ADR-011.
 *
 * @since v0.8
 */
public class IdempotencyResolver {

    /** Replay strategy for a tool during crash recovery. */
    public enum ReplayStrategy {
        /** {@code @Idempotent} — safe to re-execute the tool call. */
        REPLAY,
        /** {@code @NonIdempotent} or unknown — return cached result from event log. */
        CACHED
    }

    /**
     * Determine the replay strategy for a tool.
     *
     * @param toolHandler the tool to check
     * @return {@link ReplayStrategy#REPLAY} if {@code @Idempotent}, {@link ReplayStrategy#CACHED}
     *     otherwise
     */
    public ReplayStrategy resolveStrategy(ToolHandler toolHandler) {
        if (toolHandler.getClass().isAnnotationPresent(Idempotent.class)) {
            return ReplayStrategy.REPLAY;
        }
        // @NonIdempotent OR no annotation → default safe: use cached
        return ReplayStrategy.CACHED;
    }

    /**
     * Generate an idempotency key for a tool call.
     *
     * <p>Formula: {@code SHA256(executionId + ":" + iterationIndex + ":" + toolCallIndex)}
     * truncated to 32 hex characters. See ADR-011 for the contract.
     *
     * @param executionId the execution identifier
     * @param iterationIndex the loop iteration index
     * @param toolCallIndex the tool call index within the iteration
     * @return a 32-character hex idempotency key
     */
    public String generateKey(String executionId, int iterationIndex, int toolCallIndex) {
        String input = executionId + ":" + iterationIndex + ":" + toolCallIndex;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            String fullHex = HexFormat.of().formatHex(hashBytes);
            return fullHex.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JDK
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
