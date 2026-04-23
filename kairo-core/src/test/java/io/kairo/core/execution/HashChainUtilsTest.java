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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.execution.ExecutionEvent;
import io.kairo.api.execution.ExecutionEventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link HashChainUtils}. */
class HashChainUtilsTest {

    @Nested
    @DisplayName("computeHash()")
    class ComputeHash {

        @Test
        @DisplayName("is deterministic — same inputs produce same output")
        void isDeterministic() {
            String hash1 = HashChainUtils.computeHash("prev", "payload");
            String hash2 = HashChainUtils.computeHash("prev", "payload");
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("GENESIS seed produces expected first hash")
        void genesisProducesExpectedHash() {
            String hash = HashChainUtils.computeHash(HashChainUtils.GENESIS, "{\"test\":1}");
            assertNotNull(hash);
            assertFalse(hash.isEmpty());
            // SHA-256 hex output is always 64 characters
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("different payloads produce different hashes")
        void differentPayloadsProduceDifferentHashes() {
            String hash1 = HashChainUtils.computeHash("prev", "payload-a");
            String hash2 = HashChainUtils.computeHash("prev", "payload-b");
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("different previous hashes produce different hashes")
        void differentPreviousHashesProduceDifferentHashes() {
            String hash1 = HashChainUtils.computeHash("prev-a", "payload");
            String hash2 = HashChainUtils.computeHash("prev-b", "payload");
            assertNotEquals(hash1, hash2);
        }
    }

    @Nested
    @DisplayName("verifyChain()")
    class VerifyChain {

        @Test
        @DisplayName("passes for valid chain")
        void passesForValidChain() {
            List<ExecutionEvent> events = buildValidChain(3);
            assertDoesNotThrow(() -> HashChainUtils.verifyChain(events));
        }

        @Test
        @DisplayName("passes for empty event list")
        void passesForEmptyList() {
            assertDoesNotThrow(() -> HashChainUtils.verifyChain(List.of()));
        }

        @Test
        @DisplayName("throws for tampered payload")
        void throwsForTamperedPayload() {
            List<ExecutionEvent> events = new ArrayList<>(buildValidChain(3));
            // Tamper with the middle event's payload (hash was computed with original payload)
            ExecutionEvent original = events.get(1);
            events.set(
                    1,
                    new ExecutionEvent(
                            original.eventId(),
                            original.eventType(),
                            original.timestamp(),
                            "{\"tampered\":true}",
                            original.eventHash(),
                            original.schemaVersion()));

            HashChainViolationException ex =
                    assertThrows(
                            HashChainViolationException.class,
                            () -> HashChainUtils.verifyChain(events));
            assertEquals(original.eventId(), ex.eventId());
            assertNotNull(ex.expectedHash());
            assertEquals(original.eventHash(), ex.actualHash());
        }

        @Test
        @DisplayName("throws for tampered hash")
        void throwsForTamperedHash() {
            List<ExecutionEvent> events = new ArrayList<>(buildValidChain(3));
            ExecutionEvent original = events.get(1);
            events.set(
                    1,
                    new ExecutionEvent(
                            original.eventId(),
                            original.eventType(),
                            original.timestamp(),
                            original.payloadJson(),
                            "000000deadbeef",
                            original.schemaVersion()));

            assertThrows(
                    HashChainViolationException.class, () -> HashChainUtils.verifyChain(events));
        }

        @Test
        @DisplayName("single-event chain validates correctly")
        void singleEventChain() {
            List<ExecutionEvent> events = buildValidChain(1);
            assertDoesNotThrow(() -> HashChainUtils.verifyChain(events));
        }
    }

    @Nested
    @DisplayName("HashChainViolationException")
    class ViolationException {

        @Test
        @DisplayName("exception fields are accessible")
        void exceptionFieldsAccessible() {
            var ex = new HashChainViolationException("evt-1", "expected-abc", "actual-xyz");
            assertEquals("evt-1", ex.eventId());
            assertEquals("expected-abc", ex.expectedHash());
            assertEquals("actual-xyz", ex.actualHash());
            assertTrue(ex.getMessage().contains("evt-1"));
        }
    }

    /** Build a valid hash chain of the given size. */
    private static List<ExecutionEvent> buildValidChain(int size) {
        List<ExecutionEvent> events = new ArrayList<>();
        String previousHash = HashChainUtils.GENESIS;
        for (int i = 0; i < size; i++) {
            String payload = "{\"index\":" + i + "}";
            String hash = HashChainUtils.computeHash(previousHash, payload);
            events.add(
                    new ExecutionEvent(
                            UUID.randomUUID().toString(),
                            ExecutionEventType.MODEL_CALL_REQUEST,
                            Instant.now(),
                            payload,
                            hash,
                            1));
            previousHash = hash;
        }
        return events;
    }
}
