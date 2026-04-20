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
package io.kairo.core.memory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ConversationMemoryTest {

    private InMemoryStore store;
    private ConversationMemory memory;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        memory = new ConversationMemory(store, "test-agent");
    }

    @Test
    @DisplayName("remember stores entry with correct tag")
    void testRememberStoresEntry() {
        StepVerifier.create(memory.remember("user-name", "Alice")).verifyComplete();

        assertEquals(1, store.size());
    }

    @Test
    @DisplayName("recall retrieves stored value")
    void testRecallRetrievesValue() {
        memory.remember("preference", "dark mode").block();

        StepVerifier.create(memory.recall("preference")).expectNext("dark mode").verifyComplete();
    }

    @Test
    @DisplayName("recall returns empty for non-existent key")
    void testRecallNonExistentKey() {
        StepVerifier.create(memory.recall("nonexistent")).verifyComplete();
    }

    @Test
    @DisplayName("forget removes entry")
    void testForgetRemovesEntry() {
        memory.remember("temp-data", "some value").block();
        assertEquals(1, store.size());

        StepVerifier.create(memory.forget("temp-data")).verifyComplete();

        assertEquals(0, store.size());

        StepVerifier.create(memory.recall("temp-data")).verifyComplete();
    }

    @Test
    @DisplayName("remember multiple keys and recall individually")
    void testMultipleKeysRecall() {
        memory.remember("key1", "value1").block();
        memory.remember("key2", "value2").block();

        StepVerifier.create(memory.recall("key1")).expectNext("value1").verifyComplete();
        StepVerifier.create(memory.recall("key2")).expectNext("value2").verifyComplete();
    }

    @Test
    @DisplayName("different agents have isolated memories")
    void testAgentIsolation() {
        ConversationMemory agentA = new ConversationMemory(store, "agent-A");
        ConversationMemory agentB = new ConversationMemory(store, "agent-B");

        agentA.remember("secret", "A's secret").block();
        agentB.remember("secret", "B's secret").block();

        StepVerifier.create(agentA.recall("secret")).expectNext("A's secret").verifyComplete();
        StepVerifier.create(agentB.recall("secret")).expectNext("B's secret").verifyComplete();
    }
}
