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
package io.kairo.core.context.source;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.context.ContextSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemInfoContextSourceTest {

    private SystemInfoContextSource source;

    @BeforeEach
    void setUp() {
        source = new SystemInfoContextSource();
    }

    @Test
    void implementsContextSource() {
        assertInstanceOf(ContextSource.class, source);
    }

    @Test
    void nameIsSystemInfo() {
        assertEquals("system-info", source.getName());
    }

    @Test
    void priorityIsTen() {
        assertEquals(10, source.priority());
    }

    @Test
    void isAlwaysActive() {
        assertTrue(source.isActive());
    }

    @Test
    void collectDoesNotThrow() {
        assertDoesNotThrow(() -> source.collect());
    }

    @Test
    void collectReturnsNonNull() {
        assertNotNull(source.collect());
    }

    @Test
    void collectIsNotEmpty() {
        assertFalse(source.collect().isEmpty());
    }

    @Test
    void collectContainsSystemLine() {
        String result = source.collect();
        assertTrue(result.contains("System:"), "Expected 'System:' in output: " + result);
    }

    @Test
    void collectContainsJavaLine() {
        String result = source.collect();
        assertTrue(result.contains("Java:"), "Expected 'Java:' in output: " + result);
    }

    @Test
    void collectContainsWorkingDirectoryLine() {
        String result = source.collect();
        assertTrue(
                result.contains("Working Directory:"),
                "Expected 'Working Directory:' in output: " + result);
    }

    @Test
    void collectIsCached() {
        String first = source.collect();
        String second = source.collect();
        assertSame(first, second);
    }
}
