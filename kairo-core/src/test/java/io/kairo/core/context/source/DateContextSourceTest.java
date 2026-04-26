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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DateContextSourceTest {

    private DateContextSource source;

    @BeforeEach
    void setUp() {
        source = new DateContextSource();
    }

    @Test
    void getNameReturnsDate() {
        assertThat(source.getName()).isEqualTo("date");
    }

    @Test
    void priorityIsFive() {
        assertThat(source.priority()).isEqualTo(5);
    }

    @Test
    void isActiveReturnsTrue() {
        assertThat(source.isActive()).isTrue();
    }

    @Test
    void collectReturnsNonBlank() {
        assertThat(source.collect()).isNotBlank();
    }

    @Test
    void collectStartsWithCurrentDatePrefix() {
        assertThat(source.collect()).startsWith("Current date:");
    }

    @Test
    void collectContainsCurrentYear() {
        int year = LocalDate.now().getYear();
        assertThat(source.collect()).contains(String.valueOf(year));
    }

    @Test
    void collectMatchesIsoDateFormat() {
        String result = source.collect();
        // "Current date: YYYY-MM-DD"
        assertThat(result).matches("Current date: \\d{4}-\\d{2}-\\d{2}");
    }
}
