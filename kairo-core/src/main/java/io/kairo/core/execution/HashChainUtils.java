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

import io.kairo.api.execution.ExecutionEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Shared utilities for SHA-256 hash chain computation and verification.
 *
 * <p>The hash chain formula is {@code SHA256(previousHash + payloadJson)}, starting from the {@link
 * #GENESIS} seed. Both {@link ExecutionEventEmitter} (write path) and {@link RecoveryHandler} (read
 * path) use this class to ensure consistent hashing.
 *
 * @since v0.8
 */
public final class HashChainUtils {

    /** The genesis seed for the first event's hash computation. */
    public static final String GENESIS = "GENESIS";

    private HashChainUtils() {}

    /**
     * Compute the SHA-256 hash for a single chain link: {@code SHA256(previousHash + payload)}.
     *
     * @param previousHash the previous hash in the chain (or {@link #GENESIS} for the first event)
     * @param payload the event payload
     * @return hex-encoded SHA-256 hash
     */
    public static String computeHash(String previousHash, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((previousHash + payload).getBytes(StandardCharsets.UTF_8));
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JDK
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Verify the integrity of a hash chain.
     *
     * <p>Walks the event list from the beginning, recomputing each hash from {@link #GENESIS} and
     * comparing it against the stored {@link ExecutionEvent#eventHash()}. Throws on the first
     * mismatch.
     *
     * @param events the ordered list of events to verify
     * @throws HashChainViolationException if any event's hash does not match the expected value
     */
    public static void verifyChain(List<ExecutionEvent> events) {
        String previousHash = GENESIS;
        for (ExecutionEvent event : events) {
            String expectedHash = computeHash(previousHash, event.payloadJson());
            if (!expectedHash.equals(event.eventHash())) {
                throw new HashChainViolationException(
                        event.eventId(), expectedHash, event.eventHash());
            }
            previousHash = event.eventHash();
        }
    }
}
