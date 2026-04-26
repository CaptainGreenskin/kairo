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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemInfoContextSourceTest {

    private SystemInfoContextSource source;

    @BeforeEach
    void setUp() {
        source = new SystemInfoContextSource();
    }

    @Test
    void getNameReturnsSystemInfo() {
        assertThat(source.getName()).isEqualTo("system-info");
    }

    @Test
    void priorityIsTen() {
        assertThat(source.priority()).isEqualTo(10);
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
    void collectContainsSystemPrefix() {
        assertThat(source.collect()).contains("System:");
    }

    @Test
    void collectContainsJavaPrefix() {
        assertThat(source.collect()).contains("Java:");
    }

    @Test
    void collectContainsWorkingDirectory() {
        assertThat(source.collect()).contains("Working Directory:");
    }

    @Test
    void collectReturnsSameInstanceOnSecondCall() {
        String first = source.collect();
        String second = source.collect();
        assertThat(first).isSameAs(second);
    }
}
