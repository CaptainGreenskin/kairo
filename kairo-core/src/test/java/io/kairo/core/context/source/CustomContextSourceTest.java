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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CustomContextSourceTest {

    @Test
    void implementsContextSource() {
        assertThat(CustomContextSource.of("s", 10, () -> "content"))
                .isInstanceOf(ContextSource.class);
    }

    @Test
    void threeArgFactory_nameAccessor() {
        CustomContextSource src = CustomContextSource.of("my-source", 20, () -> "data");
        assertThat(src.getName()).isEqualTo("my-source");
    }

    @Test
    void threeArgFactory_priorityAccessor() {
        CustomContextSource src = CustomContextSource.of("s", 42, () -> "data");
        assertThat(src.priority()).isEqualTo(42);
    }

    @Test
    void threeArgFactory_isAlwaysActive() {
        CustomContextSource src = CustomContextSource.of("s", 1, () -> "data");
        assertThat(src.isActive()).isTrue();
    }

    @Test
    void threeArgFactory_collectReturnsSupplierContent() {
        CustomContextSource src = CustomContextSource.of("s", 1, () -> "hello world");
        assertThat(src.collect()).isEqualTo("hello world");
    }

    @Test
    void fourArgFactory_activeToggleRespected() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        CustomContextSource src = CustomContextSource.of("s", 1, () -> "data", enabled::get);
        assertThat(src.isActive()).isTrue();
        enabled.set(false);
        assertThat(src.isActive()).isFalse();
    }

    @Test
    void fourArgFactory_contentSupplierCalledOnCollect() {
        CustomContextSource src =
                CustomContextSource.of("s", 1, () -> "dynamic content", () -> true);
        assertThat(src.collect()).isEqualTo("dynamic content");
    }

    @Test
    void toStringContainsName() {
        assertThat(CustomContextSource.of("git-status", 15, () -> "").toString())
                .contains("git-status");
    }

    @Test
    void collect_dynamicSupplierCalledEachTime() {
        int[] counter = {0};
        CustomContextSource src = CustomContextSource.of("s", 1, () -> "call#" + (++counter[0]));
        assertThat(src.collect()).isEqualTo("call#1");
        assertThat(src.collect()).isEqualTo("call#2");
    }
}
