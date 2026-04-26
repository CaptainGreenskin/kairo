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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DateContextSourceTest {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private DateContextSource source;

    @BeforeEach
    void setUp() {
        source = new DateContextSource();
    }

    @Test
    void implementsContextSource() {
        assertInstanceOf(ContextSource.class, source);
    }

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
    void collectHasCurrentDatePrefix() {
        String result = source.collect();
        assertTrue(
                result.startsWith("Current date:"),
                "Expected prefix 'Current date:' but got: " + result);
    }

    @Test
    void collectContainsYyyyMmDdDate() {
        String result = source.collect();
        assertTrue(DATE_PATTERN.matcher(result).find(), "Expected yyyy-MM-dd date in: " + result);
    }

    @Test
    void collectContainsTodaysDate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String result = source.collect();
        assertTrue(result.contains(today), "Expected today's date " + today + " in: " + result);
    }

    @Test
    void collectIsNotCached() {
        String first = source.collect();
        String second = source.collect();
        // Both calls should be equal in value (same day), but not necessarily the same instance
        assertEquals(first, second);
    }
}
