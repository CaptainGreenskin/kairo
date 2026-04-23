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

import io.kairo.api.tool.*;
import io.kairo.core.execution.IdempotencyResolver.ReplayStrategy;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link IdempotencyResolver}. */
class IdempotencyResolverTest {

    private IdempotencyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new IdempotencyResolver();
    }

    // --- Test tool implementations ---

    @Idempotent("safe to replay")
    static class IdempotentTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            return new ToolResult("id", "result", false, Map.of());
        }
    }

    @NonIdempotent("has side effects")
    static class NonIdempotentTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            return new ToolResult("id", "result", false, Map.of());
        }
    }

    static class UnannotatedTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            return new ToolResult("id", "result", false, Map.of());
        }
    }

    @Nested
    @DisplayName("resolveStrategy()")
    class ResolveStrategy {

        @Test
        @DisplayName("@Idempotent tool returns REPLAY")
        void idempotentReturnsReplay() {
            assertEquals(ReplayStrategy.REPLAY, resolver.resolveStrategy(new IdempotentTool()));
        }

        @Test
        @DisplayName("@NonIdempotent tool returns CACHED")
        void nonIdempotentReturnsCached() {
            assertEquals(ReplayStrategy.CACHED, resolver.resolveStrategy(new NonIdempotentTool()));
        }

        @Test
        @DisplayName("unannotated tool returns CACHED (default safe)")
        void unannotatedReturnsCached() {
            assertEquals(ReplayStrategy.CACHED, resolver.resolveStrategy(new UnannotatedTool()));
        }
    }

    @Nested
    @DisplayName("generateKey()")
    class GenerateKey {

        @Test
        @DisplayName("deterministic for same inputs")
        void deterministicForSameInputs() {
            String key1 = resolver.generateKey("exec-1", 0, 0);
            String key2 = resolver.generateKey("exec-1", 0, 0);
            assertEquals(key1, key2);
        }

        @Test
        @DisplayName("different for different inputs")
        void differentForDifferentInputs() {
            String key1 = resolver.generateKey("exec-1", 0, 0);
            String key2 = resolver.generateKey("exec-1", 0, 1);
            String key3 = resolver.generateKey("exec-1", 1, 0);
            String key4 = resolver.generateKey("exec-2", 0, 0);

            assertNotEquals(key1, key2);
            assertNotEquals(key1, key3);
            assertNotEquals(key1, key4);
        }

        @Test
        @DisplayName("output is 32 hex chars")
        void outputIs32HexChars() {
            String key = resolver.generateKey("exec-1", 5, 3);
            assertEquals(32, key.length());
            assertTrue(key.matches("[0-9a-f]{32}"), "Key should be hex: " + key);
        }
    }
}
