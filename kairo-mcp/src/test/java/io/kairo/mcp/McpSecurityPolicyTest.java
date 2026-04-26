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
    void threeValuesExist() {
        McpSecurityPolicy[] values = McpSecurityPolicy.values();
        assertEquals(3, values.length);
    }

    @Test
    void allowAllValueExists() {
        assertEquals(McpSecurityPolicy.ALLOW_ALL, McpSecurityPolicy.valueOf("ALLOW_ALL"));
    }

    @Test
    void denySafeValueExists() {
        assertEquals(McpSecurityPolicy.DENY_SAFE, McpSecurityPolicy.valueOf("DENY_SAFE"));
    }

    @Test
    void denyAllValueExists() {
        assertEquals(McpSecurityPolicy.DENY_ALL, McpSecurityPolicy.valueOf("DENY_ALL"));
    }

    @Test
    void valuesAreDistinct() {
        assertNotEquals(McpSecurityPolicy.ALLOW_ALL, McpSecurityPolicy.DENY_SAFE);
        assertNotEquals(McpSecurityPolicy.DENY_SAFE, McpSecurityPolicy.DENY_ALL);
        assertNotEquals(McpSecurityPolicy.ALLOW_ALL, McpSecurityPolicy.DENY_ALL);
    }

    @Test
    void isEnum() {
        assertTrue(McpSecurityPolicy.class.isEnum());
    }
}
