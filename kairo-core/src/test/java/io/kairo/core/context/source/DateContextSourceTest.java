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

import io.kairo.api.context.ContextSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class DateContextSourceTest {

    private final DateContextSource source = new DateContextSource();

    @Test
    void implementsContextSource() {
        assertThat(source).isInstanceOf(ContextSource.class);
    }

    @Test
    void nameIsDate() {
        assertThat(source.getName()).isEqualTo("date");
    }

    @Test
    void priorityIsFive() {
        assertThat(source.priority()).isEqualTo(5);
    }

    @Test
    void isAlwaysActive() {
        assertThat(source.isActive()).isTrue();
    }

    @Test
    void collectReturnsTodaysDate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String result = source.collect();
        assertThat(result).contains(today);
    }

    @Test
    void collectStartsWithCurrentDate() {
        String result = source.collect();
        assertThat(result).startsWith("Current date:");
    }

    @Test
    void collectDoesNotThrow() {
        assertThat(source.collect()).isNotBlank();
    }
}
