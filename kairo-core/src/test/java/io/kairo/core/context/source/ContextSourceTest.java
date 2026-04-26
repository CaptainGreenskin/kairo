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
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for DateContextSource, SystemInfoContextSource, and CustomContextSource. */
class ContextSourceTest {

    // ===== DateContextSource =====

    @Test
    void dateContextSource_getName_returnsDate() {
        assertThat(new DateContextSource().getName()).isEqualTo("date");
    }

    @Test
    void dateContextSource_priority_returnsFive() {
        assertThat(new DateContextSource().priority()).isEqualTo(5);
    }

    @Test
    void dateContextSource_isActive_returnsTrue() {
        assertThat(new DateContextSource().isActive()).isTrue();
    }

    @Test
    void dateContextSource_collect_returnsCurrentDateFormatted() {
        String expected =
                "Current date: "
                        + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        assertThat(new DateContextSource().collect()).isEqualTo(expected);
    }

    // ===== SystemInfoContextSource =====

    @Test
    void systemInfoContextSource_getName_returnsSystemInfo() {
        assertThat(new SystemInfoContextSource().getName()).isEqualTo("system-info");
    }

    @Test
    void systemInfoContextSource_priority_returnsTen() {
        assertThat(new SystemInfoContextSource().priority()).isEqualTo(10);
    }

    @Test
    void systemInfoContextSource_collect_containsSystemAndJavaLines() {
        String result = new SystemInfoContextSource().collect();
        assertThat(result).contains("System:");
        assertThat(result).contains("Java:");
    }

    @Test
    void systemInfoContextSource_collect_cachesResult() {
        SystemInfoContextSource source = new SystemInfoContextSource();
        String first = source.collect();
        String second = source.collect();
        assertThat(first).isSameAs(second);
    }

    // ===== CustomContextSource =====

    @Test
    void customContextSource_of_returnsCorrectNameAndPriority() {
        CustomContextSource src = CustomContextSource.of("my-source", 42, () -> "content");
        assertThat(src.getName()).isEqualTo("my-source");
        assertThat(src.priority()).isEqualTo(42);
    }

    @Test
    void customContextSource_of_collectsFromSupplier() {
        CustomContextSource src = CustomContextSource.of("test", 1, () -> "hello world");
        assertThat(src.collect()).isEqualTo("hello world");
    }

    @Test
    void customContextSource_of_isActiveByDefault() {
        CustomContextSource src = CustomContextSource.of("test", 1, () -> "x");
        assertThat(src.isActive()).isTrue();
    }

    @Test
    void customContextSource_withActiveToggle_respectsActiveSupplier() {
        AtomicInteger callCount = new AtomicInteger(0);
        CustomContextSource src =
                CustomContextSource.of(
                        "toggleable", 10, () -> "data", () -> callCount.getAndIncrement() % 2 == 0);
        assertThat(src.isActive()).isTrue();
        assertThat(src.isActive()).isFalse();
        assertThat(src.isActive()).isTrue();
    }

    @Test
    void customContextSource_supplierCalledEachTime_noCache() {
        AtomicInteger counter = new AtomicInteger(0);
        CustomContextSource src =
                CustomContextSource.of("counting", 5, () -> "call-" + counter.incrementAndGet());
        assertThat(src.collect()).isEqualTo("call-1");
        assertThat(src.collect()).isEqualTo("call-2");
        assertThat(src.collect()).isEqualTo("call-3");
    }
}
