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
package io.kairo.mcp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class McpSecurityPolicyTest {

    @Test
    void exactlyThreeValues() {
        assertEquals(3, McpSecurityPolicy.values().length);
    }

    @Test
    void allowAllHasCorrectName() {
        assertEquals("ALLOW_ALL", McpSecurityPolicy.ALLOW_ALL.name());
        assertEquals(McpSecurityPolicy.ALLOW_ALL, McpSecurityPolicy.valueOf("ALLOW_ALL"));
    }

    @Test
    void denySafeHasCorrectName() {
        assertEquals("DENY_SAFE", McpSecurityPolicy.DENY_SAFE.name());
        assertEquals(McpSecurityPolicy.DENY_SAFE, McpSecurityPolicy.valueOf("DENY_SAFE"));
    }

    @Test
    void denyAllHasCorrectName() {
        assertEquals("DENY_ALL", McpSecurityPolicy.DENY_ALL.name());
        assertEquals(McpSecurityPolicy.DENY_ALL, McpSecurityPolicy.valueOf("DENY_ALL"));
    }

    @Test
    void ordinalsAreStable() {
        assertEquals(0, McpSecurityPolicy.ALLOW_ALL.ordinal());
        assertEquals(1, McpSecurityPolicy.DENY_SAFE.ordinal());
        assertEquals(2, McpSecurityPolicy.DENY_ALL.ordinal());
    }

    @Test
    void valueOfUnknownThrowsIllegalArgument() {
        assertThrows(
                IllegalArgumentException.class, () -> McpSecurityPolicy.valueOf("PERMIT_NONE"));
    }

    @Test
    void valuesContainsAllConstants() {
        var values = McpSecurityPolicy.values();
        assertEquals(McpSecurityPolicy.ALLOW_ALL, values[0]);
        assertEquals(McpSecurityPolicy.DENY_SAFE, values[1]);
        assertEquals(McpSecurityPolicy.DENY_ALL, values[2]);
    }
}
