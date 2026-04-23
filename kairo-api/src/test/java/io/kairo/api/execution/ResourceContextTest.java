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
package io.kairo.api.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link ResourceContext} record. */
class ResourceContextTest {

    @Test
    @DisplayName("valid construction with all fields")
    void validConstruction() {
        ResourceContext ctx =
                new ResourceContext(5, 1500L, Duration.ofSeconds(30), "test-agent", "state");
        assertEquals(5, ctx.iteration());
        assertEquals(1500L, ctx.tokensUsed());
        assertEquals(Duration.ofSeconds(30), ctx.elapsed());
        assertEquals("test-agent", ctx.agentName());
        assertEquals("state", ctx.agentState());
    }

    @Test
    @DisplayName("null agentState is allowed (it is @Nullable)")
    void nullAgentStateIsAllowed() {
        ResourceContext ctx = new ResourceContext(0, 0L, Duration.ZERO, "agent", null);
        assertNull(ctx.agentState());
    }

    @Test
    @DisplayName("null elapsed throws NullPointerException")
    void nullElapsedThrows() {
        assertThrows(
                NullPointerException.class, () -> new ResourceContext(0, 0L, null, "agent", null));
    }

    @Test
    @DisplayName("null agentName throws NullPointerException")
    void nullAgentNameThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new ResourceContext(0, 0L, Duration.ZERO, null, null));
    }
}
