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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CustomContextSourceTest {

    @Test
    void simpleFactoryImplementsContextSource() {
        CustomContextSource src = CustomContextSource.of("test", 10, () -> "content");
        assertInstanceOf(ContextSource.class, src);
    }

    @Test
    void simpleFactoryNameIsCorrect() {
        CustomContextSource src = CustomContextSource.of("my-source", 5, () -> "");
        assertEquals("my-source", src.getName());
    }

    @Test
    void simpleFactoryPriorityIsCorrect() {
        CustomContextSource src = CustomContextSource.of("src", 42, () -> "");
        assertEquals(42, src.priority());
    }

    @Test
    void simpleFactoryIsAlwaysActive() {
        CustomContextSource src = CustomContextSource.of("src", 1, () -> "");
        assertTrue(src.isActive());
    }

    @Test
    void simpleFactoryCollectReturnsSupplierValue() {
        CustomContextSource src = CustomContextSource.of("src", 1, () -> "hello world");
        assertEquals("hello world", src.collect());
    }

    @Test
    void simpleFactoryCollectInvokesSupplierEachTime() {
        AtomicInteger count = new AtomicInteger(0);
        CustomContextSource src =
                CustomContextSource.of("src", 1, () -> "v" + count.incrementAndGet());
        src.collect();
        src.collect();
        assertEquals(2, count.get());
    }

    @Test
    void activeToggleFactoryWithActiveSuppliertrue() {
        CustomContextSource src = CustomContextSource.of("s", 1, () -> "c", () -> true);
        assertTrue(src.isActive());
    }

    @Test
    void activeToggleFactoryWithActiveSupplierFalse() {
        CustomContextSource src = CustomContextSource.of("s", 1, () -> "c", () -> false);
        assertFalse(src.isActive());
    }

    @Test
    void activeToggleFactoryNameAndPriority() {
        CustomContextSource src = CustomContextSource.of("toggled", 99, () -> "data", () -> true);
        assertEquals("toggled", src.getName());
        assertEquals(99, src.priority());
    }

    @Test
    void activeToggleCanChangeDynamically() {
        boolean[] flag = {true};
        CustomContextSource src = CustomContextSource.of("dyn", 1, () -> "v", () -> flag[0]);
        assertTrue(src.isActive());
        flag[0] = false;
        assertFalse(src.isActive());
    }

    @Test
    void toStringContainsName() {
        CustomContextSource src = CustomContextSource.of("my-ctx", 1, () -> "");
        assertTrue(src.toString().contains("my-ctx"));
    }
}
