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

/**
 * Thrown when hash chain verification detects a mismatch during recovery.
 *
 * <p>Indicates that an event's stored hash does not match the expected hash recomputed from the
 * chain. This may signal data corruption or tampering.
 *
 * @since v0.8
 */
public class HashChainViolationException extends RuntimeException {

    private final String eventId;
    private final String expectedHash;
    private final String actualHash;

    /**
     * Create a new hash chain violation exception.
     *
     * @param eventId the ID of the event where the mismatch was detected
     * @param expectedHash the hash recomputed from the chain
     * @param actualHash the hash stored on the event
     */
    public HashChainViolationException(String eventId, String expectedHash, String actualHash) {
        super(
                "Hash chain verification failed at event "
                        + eventId
                        + ": expected="
                        + expectedHash
                        + ", actual="
                        + actualHash);
        this.eventId = eventId;
        this.expectedHash = expectedHash;
        this.actualHash = actualHash;
    }

    /** Return the ID of the event where the mismatch was detected. */
    public String eventId() {
        return eventId;
    }

    /** Return the expected hash recomputed from the chain. */
    public String expectedHash() {
        return expectedHash;
    }

    /** Return the actual hash stored on the event. */
    public String actualHash() {
        return actualHash;
    }
}
