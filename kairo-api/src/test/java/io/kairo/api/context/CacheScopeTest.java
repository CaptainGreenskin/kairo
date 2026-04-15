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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CacheScopeTest {

    // ---- CacheScope enum ----

    @Test
    void enumValues_containsAllScopes() {
        CacheScope[] values = CacheScope.values();
        assertEquals(3, values.length);
        assertEquals(CacheScope.GLOBAL, values[0]);
        assertEquals(CacheScope.SESSION, values[1]);
        assertEquals(CacheScope.NONE, values[2]);
    }

    @Test
    void valueOf_roundTrips() {
        for (CacheScope scope : CacheScope.values()) {
            assertEquals(scope, CacheScope.valueOf(scope.name()));
        }
    }

    // ---- SystemPromptSegment record ----

    @Test
    void construction_andFieldAccess() {
        var segment = new SystemPromptSegment("identity", "You are helpful.", CacheScope.GLOBAL);
        assertEquals("identity", segment.name());
        assertEquals("You are helpful.", segment.content());
        assertEquals(CacheScope.GLOBAL, segment.scope());
    }

    @Test
    void isCacheable_globalReturnsTrue() {
        var segment = new SystemPromptSegment("x", "content", CacheScope.GLOBAL);
        assertTrue(segment.isCacheable());
    }

    @Test
    void isCacheable_sessionReturnsTrue() {
        var segment = new SystemPromptSegment("x", "content", CacheScope.SESSION);
        assertTrue(segment.isCacheable());
    }

    @Test
    void isCacheable_noneReturnsFalse() {
        var segment = new SystemPromptSegment("x", "content", CacheScope.NONE);
        assertFalse(segment.isCacheable());
    }

    @Test
    void factoryMethod_global() {
        var segment = SystemPromptSegment.global("identity", "You are helpful.");
        assertEquals("identity", segment.name());
        assertEquals("You are helpful.", segment.content());
        assertEquals(CacheScope.GLOBAL, segment.scope());
        assertTrue(segment.isCacheable());
    }

    @Test
    void factoryMethod_session() {
        var segment = SystemPromptSegment.session("tools", "Available tools: read, write");
        assertEquals("tools", segment.name());
        assertEquals("Available tools: read, write", segment.content());
        assertEquals(CacheScope.SESSION, segment.scope());
        assertTrue(segment.isCacheable());
    }

    @Test
    void factoryMethod_dynamic() {
        var segment = SystemPromptSegment.dynamic("context", "Date: 2026-04-15");
        assertEquals("context", segment.name());
        assertEquals("Date: 2026-04-15", segment.content());
        assertEquals(CacheScope.NONE, segment.scope());
        assertFalse(segment.isCacheable());
    }

    @Test
    void recordEquality() {
        var a = SystemPromptSegment.global("id", "content");
        var b = new SystemPromptSegment("id", "content", CacheScope.GLOBAL);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordToString_containsFields() {
        var segment = SystemPromptSegment.global("identity", "text");
        String str = segment.toString();
        assertTrue(str.contains("identity"));
        assertTrue(str.contains("text"));
        assertTrue(str.contains("GLOBAL"));
    }
}
