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
package io.kairo.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class KairoObservabilityTest {

    @Test
    void moduleNameIsKairoObservability() {
        assertEquals("kairo-observability", KairoObservability.MODULE_NAME);
    }

    @Test
    void moduleVersionIsNotNull() {
        assertNotNull(KairoObservability.MODULE_VERSION);
    }

    @Test
    void moduleVersionIsNotBlank() {
        assertNotNull(KairoObservability.MODULE_VERSION);
        assert !KairoObservability.MODULE_VERSION.isBlank();
    }

    @Test
    void moduleNameIsNotBlank() {
        assert !KairoObservability.MODULE_NAME.isBlank();
    }
}
