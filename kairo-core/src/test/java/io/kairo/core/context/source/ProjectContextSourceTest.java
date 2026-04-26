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

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ProjectContextSource}. */
class ProjectContextSourceTest {

    private final ProjectContextSource source = new ProjectContextSource();

    @Test
    void getName_returnsProjectStructure() {
        assertThat(source.getName()).isEqualTo("project-structure");
    }

    @Test
    void priority_returnsTwenty() {
        assertThat(source.priority()).isEqualTo(20);
    }

    @Test
    void isActive_returnsTrue() {
        assertThat(source.isActive()).isTrue();
    }

    @Test
    void collect_returnsNonEmptyString() {
        assertThat(source.collect()).isNotEmpty();
    }

    @Test
    void collect_startsWithProjectStructureHeader() {
        assertThat(source.collect()).startsWith("Project structure (");
    }

    @Test
    void collect_cachesResult() {
        String first = source.collect();
        String second = source.collect();
        assertThat(first).isSameAs(second);
    }

    @Test
    void collect_containsSrcDirectory() {
        // kairo-core has a src/ directory in its working directory
        assertThat(source.collect()).contains("src/");
    }
}
