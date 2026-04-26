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

import org.junit.jupiter.api.Test;

class SystemInfoContextSourceTest {

    private final SystemInfoContextSource source = new SystemInfoContextSource();

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
    void collectContainsSystemLine() {
        String output = source.collect();
        assertTrue(output.contains("System:"), "Expected 'System:' in output: " + output);
    }

    @Test
    void collectContainsJavaLine() {
        String output = source.collect();
        assertTrue(output.contains("Java:"), "Expected 'Java:' in output: " + output);
    }

    @Test
    void collectContainsWorkingDirectoryLine() {
        String output = source.collect();
        assertTrue(
                output.contains("Working Directory:"),
                "Expected 'Working Directory:' in output: " + output);
    }

    @Test
    void collectIsCachedAcrossCalls() {
        String first = source.collect();
        String second = source.collect();
        assertSame(first, second, "Expected the same String instance to be returned (cached)");
    }

    @Test
    void collectContainsActualJavaVersion() {
        String javaVersion = System.getProperty("java.version");
        String output = source.collect();
        assertTrue(
                output.contains(javaVersion),
                "Expected java.version " + javaVersion + " in output: " + output);
    }

    @Test
    void collectContainsWorkingDirectory() {
        String userDir = System.getProperty("user.dir");
        String output = source.collect();
        assertTrue(
                output.contains(userDir), "Expected user.dir " + userDir + " in output: " + output);
    }
}
