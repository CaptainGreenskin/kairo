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
import org.junit.jupiter.api.Test;

class DateContextSourceTest {

    private final DateContextSource source = new DateContextSource();

    @Test
    void nameIsDate() {
        assertEquals("date", source.getName());
    }

    @Test
    void priorityIsFive() {
        assertEquals(5, source.priority());
    }

    @Test
    void isAlwaysActive() {
        assertTrue(source.isActive());
    }

    @Test
    void collectStartsWithCurrentDatePrefix() {
        String output = source.collect();
        assertTrue(
                output.startsWith("Current date: "),
                "Expected prefix 'Current date: ' in: " + output);
    }

    @Test
    void collectContainsTodaysDate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String output = source.collect();
        assertTrue(output.contains(today), "Expected today's date " + today + " in: " + output);
    }

    @Test
    void collectMatchesDatePattern() {
        String output = source.collect();
        // Format: "Current date: yyyy-MM-dd"
        assertTrue(
                output.matches("Current date: \\d{4}-\\d{2}-\\d{2}"),
                "Output did not match expected date pattern: " + output);
    }

    @Test
    void multipleCallsReturnConsistentFormat() {
        String first = source.collect();
        String second = source.collect();
        // Both should match the same pattern (same day)
        assertTrue(first.matches("Current date: \\d{4}-\\d{2}-\\d{2}"));
        assertTrue(second.matches("Current date: \\d{4}-\\d{2}-\\d{2}"));
    }
}
