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
package io.kairo.api.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolContextTest {

    @Test
    @DisplayName("ToolContext stores agent and session IDs")
    void basicFields() {
        ToolContext ctx = new ToolContext("agent-1", "session-42", Map.of("db", "postgres"));

        assertEquals("agent-1", ctx.agentId());
        assertEquals("session-42", ctx.sessionId());
        assertEquals(Map.of("db", "postgres"), ctx.dependencies());
    }

    @Test
    @DisplayName("Null dependencies defaults to empty map")
    void nullDependenciesDefaults() {
        ToolContext ctx = new ToolContext("a", "s", null);
        assertEquals(Map.of(), ctx.dependencies());
    }

    @Test
    @DisplayName("Dependencies are defensively copied and immutable")
    void dependenciesImmutable() {
        ToolContext ctx = new ToolContext("a", "s", Map.of("key", "value"));
        assertThrows(UnsupportedOperationException.class, () -> ctx.dependencies().put("x", "y"));
    }

    @Test
    @DisplayName("ToolContext is a record with value equality")
    void valueEquality() {
        ToolContext ctx1 = new ToolContext("a", "s", Map.of());
        ToolContext ctx2 = new ToolContext("a", "s", Map.of());
        assertEquals(ctx1, ctx2);
        assertEquals(ctx1.hashCode(), ctx2.hashCode());
    }
}
