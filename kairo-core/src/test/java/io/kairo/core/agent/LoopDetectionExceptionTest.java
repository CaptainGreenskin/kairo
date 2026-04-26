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
package io.kairo.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.exception.KairoException;
import org.junit.jupiter.api.Test;

class LoopDetectionExceptionTest {

    @Test
    void extendsKairoException() {
        assertThat(new LoopDetectionException("loop")).isInstanceOf(KairoException.class);
    }

    @Test
    void isRuntimeException() {
        assertThat(new LoopDetectionException("loop")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void messageIsPreserved() {
        assertThat(new LoopDetectionException("detected loop at step 5").getMessage())
                .isEqualTo("detected loop at step 5");
    }

    @Test
    void canBeThrownAndCaught() {
        assertThatThrownBy(
                        () -> {
                            throw new LoopDetectionException("loop");
                        })
                .isInstanceOf(LoopDetectionException.class)
                .hasMessage("loop");
    }

    @Test
    void causeIsNullByDefault() {
        assertThat(new LoopDetectionException("loop").getCause()).isNull();
    }

    @Test
    void catchableAsKairoException() {
        assertThatThrownBy(
                        () -> {
                            throw new LoopDetectionException("loop");
                        })
                .isInstanceOf(KairoException.class);
    }
}
