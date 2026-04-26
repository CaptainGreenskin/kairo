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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContextSourcesTest {

    @Nested
    @DisplayName("DateContextSource")
    class DateContextSourceTests {
        private final DateContextSource source = new DateContextSource();

        @Test
        @DisplayName("getName() returns 'date'")
        void getName_returnsDate() {
            assertEquals("date", source.getName());
        }

        @Test
        @DisplayName("priority() returns 5")
        void priority_returns5() {
            assertEquals(5, source.priority());
        }

        @Test
        @DisplayName("isActive() returns true")
        void isActive_returnsTrue() {
            assertTrue(source.isActive());
        }

        @Test
        @DisplayName("collect() returns today's date in yyyy-MM-dd format")
        void collect_returnsTodayDate() {
            String expected =
                    "Current date: "
                            + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            assertEquals(expected, source.collect());
        }
    }

    @Nested
    @DisplayName("SystemInfoContextSource")
    class SystemInfoContextSourceTests {
        private final SystemInfoContextSource source = new SystemInfoContextSource();

        @Test
        @DisplayName("getName() returns 'system-info'")
        void getName_returnsSystemInfo() {
            assertEquals("system-info", source.getName());
        }

        @Test
        @DisplayName("priority() returns 10")
        void priority_returns10() {
            assertEquals(10, source.priority());
        }

        @Test
        @DisplayName("isActive() returns true")
        void isActive_returnsTrue() {
            assertTrue(source.isActive());
        }

        @Test
        @DisplayName("collect() contains Java version")
        void collect_containsJavaVersion() {
            String result = source.collect();
            assertTrue(result.contains("Java:"), "Should contain Java info");
        }

        @Test
        @DisplayName("collect() contains Working Directory")
        void collect_containsWorkingDirectory() {
            String result = source.collect();
            assertTrue(result.contains("Working Directory:"), "Should contain working dir");
        }

        @Test
        @DisplayName("collect() is cached — returns same value on second call")
        void collect_isCached() {
            String first = source.collect();
            String second = source.collect();
            assertSame(first, second);
        }
    }

    @Nested
    @DisplayName("ProjectContextSource")
    class ProjectContextSourceTests {
        private final ProjectContextSource source = new ProjectContextSource();

        @Test
        @DisplayName("getName() returns 'project-structure'")
        void getName_returnsProjectStructure() {
            assertEquals("project-structure", source.getName());
        }

        @Test
        @DisplayName("priority() returns 20")
        void priority_returns20() {
            assertEquals(20, source.priority());
        }

        @Test
        @DisplayName("isActive() returns true")
        void isActive_returnsTrue() {
            assertTrue(source.isActive());
        }

        @Test
        @DisplayName("collect() returns non-empty string containing project structure header")
        void collect_returnsProjectHeader() {
            String result = source.collect();
            assertFalse(result.isBlank());
            assertTrue(result.contains("Project structure"), "Should contain structure header");
        }

        @Test
        @DisplayName("collect() is cached — returns same value on second call")
        void collect_isCached() {
            String first = source.collect();
            String second = source.collect();
            assertSame(first, second);
        }
    }

    @Nested
    @DisplayName("CustomContextSource")
    class CustomContextSourceTests {

        @Test
        @DisplayName("of(name, priority, supplier) creates always-active source")
        void of_simpleFactory_alwaysActive() {
            CustomContextSource source = CustomContextSource.of("test", 42, () -> "hello");
            assertEquals("test", source.getName());
            assertEquals(42, source.priority());
            assertTrue(source.isActive());
            assertEquals("hello", source.collect());
        }

        @Test
        @DisplayName("of(name, priority, supplier, activeSupplier) respects activeSupplier")
        void of_withActiveSupplier_respectsToggle() {
            AtomicBoolean active = new AtomicBoolean(true);
            CustomContextSource source =
                    CustomContextSource.of("toggleable", 30, () -> "content", active::get);

            assertTrue(source.isActive());
            active.set(false);
            assertFalse(source.isActive());
        }

        @Test
        @DisplayName("collect() delegates to content supplier each call")
        void collect_delegatesToSupplier() {
            int[] callCount = {0};
            CustomContextSource source =
                    CustomContextSource.of("counted", 1, () -> "call#" + (++callCount[0]));

            assertEquals("call#1", source.collect());
            assertEquals("call#2", source.collect());
            assertEquals(2, callCount[0]);
        }

        @Test
        @DisplayName("toString() includes name")
        void toString_includesName() {
            CustomContextSource source = CustomContextSource.of("my-source", 5, () -> "");
            assertTrue(source.toString().contains("my-source"));
        }
    }
}
