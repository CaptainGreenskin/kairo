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
package io.kairo.core.context.compaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.kairo.api.context.ContextState;
import io.kairo.api.model.ModelProvider;
import org.junit.jupiter.api.Test;

class AutoCompactionTest {

    private final ModelProvider provider = mock(ModelProvider.class);

    @Test
    void name_returnsAuto() {
        assertThat(new AutoCompaction(provider).name()).isEqualTo("auto");
    }

    @Test
    void priority_returns400() {
        assertThat(new AutoCompaction(provider).priority()).isEqualTo(400);
    }

    @Test
    void shouldTrigger_atDefaultThreshold_returnsTrue() {
        var state = new ContextState(0, 0, 0.95f, 10);
        assertThat(new AutoCompaction(provider).shouldTrigger(state)).isTrue();
    }

    @Test
    void shouldTrigger_belowDefaultThreshold_returnsFalse() {
        var state = new ContextState(0, 0, 0.94f, 10);
        assertThat(new AutoCompaction(provider).shouldTrigger(state)).isFalse();
    }

    @Test
    void shouldTrigger_nullProvider_returnsFalse() {
        var state = new ContextState(0, 0, 0.99f, 10);
        assertThat(new AutoCompaction(null).shouldTrigger(state)).isFalse();
    }

    @Test
    void shouldTrigger_customThreshold_triggersAtCustomPressure() {
        var state = new ContextState(0, 0, 0.80f, 10);
        assertThat(new AutoCompaction(provider, 0.80f).shouldTrigger(state)).isTrue();
    }
}
