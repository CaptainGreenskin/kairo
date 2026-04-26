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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CustomContextSourceTest {

    @Test
    void ofStoresName() {
        CustomContextSource source = CustomContextSource.of("my-source", 20, () -> "content");
        assertThat(source.getName()).isEqualTo("my-source");
    }

    @Test
    void ofStoresPriority() {
        CustomContextSource source = CustomContextSource.of("s", 42, () -> "content");
        assertThat(source.priority()).isEqualTo(42);
    }

    @Test
    void isActiveAlwaysTrueForSimpleFactory() {
        CustomContextSource source = CustomContextSource.of("s", 1, () -> "content");
        assertThat(source.isActive()).isTrue();
    }

    @Test
    void collectInvokesSupplier() {
        CustomContextSource source = CustomContextSource.of("s", 1, () -> "hello");
        assertThat(source.collect()).isEqualTo("hello");
    }

    @Test
    void collectCallsSupplierEachTime() {
        AtomicInteger counter = new AtomicInteger(0);
        CustomContextSource source =
                CustomContextSource.of("s", 1, () -> "v" + counter.incrementAndGet());

        source.collect();
        source.collect();

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void activeSupplierControlsIsActive() {
        AtomicBoolean active = new AtomicBoolean(false);
        CustomContextSource source = CustomContextSource.of("s", 1, () -> "content", active::get);

        assertThat(source.isActive()).isFalse();

        active.set(true);
        assertThat(source.isActive()).isTrue();
    }

    @Test
    void ofWithActiveFourArgFactory() {
        CustomContextSource source =
                CustomContextSource.of("toggled", 15, () -> "data", () -> true);

        assertThat(source.getName()).isEqualTo("toggled");
        assertThat(source.priority()).isEqualTo(15);
        assertThat(source.isActive()).isTrue();
        assertThat(source.collect()).isEqualTo("data");
    }

    @Test
    void toStringContainsName() {
        CustomContextSource source = CustomContextSource.of("git-status", 10, () -> "");
        assertThat(source.toString()).contains("git-status");
    }
}
